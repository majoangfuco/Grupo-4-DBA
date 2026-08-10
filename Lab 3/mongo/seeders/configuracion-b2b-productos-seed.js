// ═══════════════════════════════════════════════════════════════
// configuracion-b2b-productos-seed.js · Mínimos B2B por producto
//
// Colección Mongo "configuracion_b2b_productos" — 100% paralela a
// Postgres (ver CarritoMongoServicio §"Configuración de cantidad mínima
// B2B por producto", líneas 127-153): no hay columna en producto_entidad
// para esto, es un documento por producto con SOLO {productoId,
// cantidadMinimaB2B}. Sin _id propio con significado — Mongo genera el
// ObjectId normal, el filtro/identidad real del documento es el campo
// `productoId` (así es como establecerCantidadMinima/obtenerCantidadMinima
// la consultan: Filters.eq("productoId", productoId), nunca por _id).
//
// Si el Admin nunca configuró un mínimo para un producto,
// CarritoMongoServicio.obtenerCantidadMinima devuelve null. El carrito usa
// 1 solo como piso técnico de Mongo para cantidades positivas, pero no se
// aplica una regla B2B especial hasta que exista un documento acá.
//
// Estos overrides deben ser LOS MISMOS `productoId`/valor que
// CANTIDAD_MINIMA_B2B_OVERRIDES en mongo/seeders/productos-seed.js (ese
// script fija cantidadMinimaB2B en la copia "productos" que lee
// CheckoutServicio; este script fija el mismo mínimo en la colección que
// lee CarritoMongoServicio al armar el carrito). Si se agrega/cambia un
// override en uno, hay que replicarlo acá — no hay una única fuente de
// verdad automática entre ambos seeders.
//
// Requiere haber corrido antes backendB2B/init.sql (para que estos
// producto_ID existan en Postgres) — este seeder no valida eso, solo
// escribe en Mongo.
//
// Uso:
//   mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&directConnection=true" \
//       --file mongo/seeders/configuracion-b2b-productos-seed.js
//
// Idempotente: usa upsert por el filtro {productoId}, se puede correr
// tantas veces como haga falta sin duplicar documentos.
// ═══════════════════════════════════════════════════════════════

const DB_NAME = process.env.MONGO_DB || "b2b";
// `database` y no `db`: `const db = db.getSiblingDB(...)` sombrea el `db`
// global de mongosh y revienta con "Cannot access 'db' before initialization"
// (TDZ) antes de llegar a usarlo. Mismo patrón que mongo/indexes.js.
const database = db.getSiblingDB(DB_NAME);

function log(msg) {
    print(`[configuracion-b2b-productos-seed] ${msg}`);
}

// Sin mínimos precargados: la regla B2B empieza a aplicar solo cuando el
// Admin guarda una configuración desde la interfaz.
const CANTIDAD_MINIMA_B2B_OVERRIDES = {};

const coleccion = database.getCollection("configuracion_b2b_productos");

const operaciones = Object.entries(CANTIDAD_MINIMA_B2B_OVERRIDES).map(([productoId, cantidadMinimaB2B]) => ({
    replaceOne: {
        filter: { productoId: Number(productoId) },
        replacement: { productoId: Number(productoId), cantidadMinimaB2B },
        upsert: true
    }
}));

if (operaciones.length > 0) {
    const resultado = coleccion.bulkWrite(operaciones, { ordered: true });
    log(`Seed aplicado: ${resultado.upsertedCount} insertados, ${resultado.modifiedCount} actualizados `
        + `(de ${operaciones.length} configuraciones totales).`);
} else {
    log("Seed sin mínimos precargados: no se insertaron configuraciones B2B.");
}
log('Recordatorio: productos sin documento acá no tienen mínimo B2B especial configurado.');
