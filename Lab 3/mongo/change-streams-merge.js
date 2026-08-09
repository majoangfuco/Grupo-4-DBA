// ═══════════════════════════════════════════════════════════════
// change-streams-merge.js · Lab 3, punto 6 (Vista Materializada / Change Streams)
//
// Define el lado "base de datos" de la vista materializada de PRODUCTOS MÁS
// VENDIDOS:
//
//   1. Crea la colección `productos_mas_vendidos` con su validador
//      $jsonSchema (el shape lo produce el $project del pipeline de abajo,
//      así que el validador es un contrato real, no decorativo).
//   2. Crea sus índices de consulta (ranking por unidades / por monto).
//   3. Crea la colección `change_stream_checkpoints`, donde el worker Java
//      persiste el resume token del change stream para no reprocesar (ni
//      perderse) eventos entre reinicios.
//   4. Define el pipeline de agregación materializado ($merge) y lo corre
//      una vez completo (backfill), para que la colección quede consistente
//      con las órdenes CONFIRMADAS que ya existían antes de encender el
//      worker.
//
// El listener reactivo NO vive acá: es un proceso Java aparte del server
// HTTP (`ProductosMasVendidosWorker`, perfil `worker`, servicio `worker`
// de docker-compose.yml). Este script deja la infraestructura lista y
// documenta el mismo pipeline que ese worker ejecuta en caliente.
//
// Uso:
//   mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&replicaSet=rs0" \
//       --file mongo/change-streams-merge.js
//
// Idempotente: se puede correr las veces que haga falta (collMod si la
// colección ya existe, createIndex conserva los índices previos, y el
// $merge recalcula desde cero en vez de acumular).
// ═══════════════════════════════════════════════════════════════

const DB_NAME = process.env.MONGO_DB || "b2b";
const database = db.getSiblingDB(DB_NAME);

const COL_DESTINO = "productos_mas_vendidos";
const COL_CHECKPOINTS = "change_stream_checkpoints";

function log(msg) {
    print(`[change-streams-merge] ${msg}`);
}

// ─── 1. Validador de la colección materializada ────────────────────────
// Estos campos son exactamente los que emite el $project del pipeline. Si
// alguien cambia el pipeline sin tocar este validador, el $merge falla en
// vez de escribir documentos con otro shape — que es justo lo que se
// quiere de un validador en una colección derivada.
const validadorMasVendidos = {
    $jsonSchema: {
        bsonType: "object",
        title: "Producto más vendido (vista materializada, derivada de ordenes)",
        required: [
            "_id",
            "productoId",
            "nombreProducto",
            "unidadesVendidas",
            "montoTotalVendido",
            "ordenesConfirmadas",
            "actualizadoEn"
        ],
        additionalProperties: false,
        properties: {
            // _id === productoId: el $merge hace upsert `on: "_id"`, así que
            // un producto siempre ocupa una sola fila del ranking por
            // construcción, sin necesidad de un índice único extra.
            _id: { bsonType: ["int", "long"], minimum: 1 },
            productoId: { bsonType: ["int", "long"], minimum: 1 },
            nombreProducto: { bsonType: "string", minLength: 1 },
            unidadesVendidas: { bsonType: ["int", "long"], minimum: 0 },
            montoTotalVendido: { bsonType: ["double", "decimal", "int", "long"], minimum: 0 },
            ordenesConfirmadas: { bsonType: ["int", "long"], minimum: 0 },
            ultimaVentaEn: { bsonType: ["date", "null"] },
            actualizadoEn: { bsonType: "date" }
        }
    }
};

if (database.getCollectionNames().includes(COL_DESTINO)) {
    database.runCommand({
        collMod: COL_DESTINO,
        validator: validadorMasVendidos,
        validationLevel: "strict",
        validationAction: "error"
    });
    log(`Validador actualizado sobre "${COL_DESTINO}" (collMod).`);
} else {
    database.createCollection(COL_DESTINO, {
        validator: validadorMasVendidos,
        validationLevel: "strict",
        validationAction: "error"
    });
    log(`Colección "${COL_DESTINO}" creada con validador.`);
}

// ─── 2. Índices de la vista materializada ──────────────────────────────
// El caso de uso es siempre "top N": el índice descendente permite
// resolver el ranking con un IXSCAN sin etapa SORT en memoria.
database[COL_DESTINO].createIndex(
    { unidadesVendidas: -1 },
    { name: "ix_masvendidos_unidadesVendidas" }
);
database[COL_DESTINO].createIndex(
    { montoTotalVendido: -1 },
    { name: "ix_masvendidos_montoTotalVendido" }
);

// ─── 3. Checkpoints del change stream ──────────────────────────────────
// Un documento por listener: { _id: <nombre>, resumeToken, actualizadoEn }.
// Sin esto, un reinicio del worker abriría el stream "desde ahora" y las
// órdenes confirmadas mientras estaba caído nunca entrarían al ranking.
if (!database.getCollectionNames().includes(COL_CHECKPOINTS)) {
    database.createCollection(COL_CHECKPOINTS);
    log(`Colección "${COL_CHECKPOINTS}" creada.`);
}

// ─── 4. Pipeline materializado ($merge) ────────────────────────────────
/**
 * Pipeline de la vista materializada de productos más vendidos.
 *
 * @param {Array<number>|null} idsProductos  Si viene una lista, recalcula
 *        SOLO esos productos (modo incremental que usa el worker con los
 *        productoId de la orden recién confirmada). Si viene null,
 *        recalcula el ranking completo (backfill).
 *
 * Punto clave: incluso en modo incremental cada producto se recalcula
 * SUMANDO DE CERO sobre todas sus órdenes CONFIRMADAS, no se le suma el
 * delta del evento. Eso hace la operación idempotente: si el change stream
 * reentrega un evento (resume token replay, reinicio del worker, retry),
 * el resultado es idéntico y no se cuentan ventas dos veces.
 */
function pipelineProductosMasVendidos(idsProductos) {
    const etapas = [
        // Solo órdenes CONFIRMADAS: el checkout las crea en PENDIENTE, y es
        // la confirmación la que las hace contar como venta (enunciado:
        // "cada vez que se confirma una nueva orden").
        { $match: { estado: "CONFIRMADA" } },
        { $unwind: "$items" }
    ];

    if (Array.isArray(idsProductos) && idsProductos.length > 0) {
        etapas.push({ $match: { "items.productoId": { $in: idsProductos } } });
    }

    etapas.push(
        // Ordenar por fecha antes de agrupar hace que el $last de
        // nombreProducto sea el nombre de la venta MÁS RECIENTE, y no uno
        // arbitrario (los ítems guardan un snapshot del nombre al momento
        // de comprar, así que pueden diferir entre órdenes viejas y nuevas).
        { $sort: { fechaOrden: 1 } },
        {
            $group: {
                _id: "$items.productoId",
                nombreProducto: { $last: "$items.nombreProducto" },
                unidadesVendidas: { $sum: "$items.cantidad" },
                montoTotalVendido: { $sum: "$items.subtotal" },
                ordenesConfirmadas: { $sum: 1 },
                ultimaVentaEn: { $max: { $ifNull: ["$fechaConfirmacion", "$fechaOrden"] } }
            }
        },
        { $sort: { unidadesVendidas: -1 } },
        {
            $project: {
                _id: 1,
                productoId: "$_id",
                nombreProducto: 1,
                unidadesVendidas: 1,
                montoTotalVendido: 1,
                ordenesConfirmadas: 1,
                ultimaVentaEn: 1,
                actualizadoEn: "$$NOW"
            }
        },
        {
            $merge: {
                into: COL_DESTINO,
                on: "_id",
                // "replace" y no "merge": el documento recalculado ES el
                // estado completo del producto en el ranking, no un parche.
                whenMatched: "replace",
                whenNotMatched: "insert"
            }
        }
    );

    return etapas;
}

// Backfill completo. Necesario porque el change stream solo ve lo que pasa
// de aquí en adelante: sin este $merge inicial, las órdenes ya confirmadas
// antes de encender el worker quedarían fuera del ranking para siempre.
const ordenesConfirmadas = database.ordenes.countDocuments({ estado: "CONFIRMADA" });
database.ordenes.aggregate(pipelineProductosMasVendidos(null)).toArray();

log(`Backfill listo sobre ${ordenesConfirmadas} orden(es) CONFIRMADA(s).`);
log(`"${COL_DESTINO}" quedó con ${database[COL_DESTINO].countDocuments({})} producto(s).`);
log(`Índices: ${JSON.stringify(database[COL_DESTINO].getIndexes())}`);

// Vistazo al top 5 para verificación manual tras correr el script.
const top = database[COL_DESTINO]
    .find({}, { nombreProducto: 1, unidadesVendidas: 1, montoTotalVendido: 1 })
    .sort({ unidadesVendidas: -1 })
    .limit(5)
    .toArray();

if (top.length === 0) {
    log("Top 5: (vacío — todavía no hay órdenes CONFIRMADAS)");
} else {
    log("Top 5 actual:");
    printjson(top);
}
