// Indices del dominio documental (Lab 3, punto 5).
// El script es idempotente: createIndex conserva los indices ya existentes.

const DB_NAME = process.env.MONGO_DB || "b2b";
const TTL_CARRITO_SEGUNDOS = Number.parseInt(
    process.env.MONGO_CART_TTL_SECONDS || "2592000", // 30 dias
    10
);
const database = db.getSiblingDB(DB_NAME);

if (!Number.isInteger(TTL_CARRITO_SEGUNDOS) || TTL_CARRITO_SEGUNDOS <= 0) {
    throw new Error("MONGO_CART_TTL_SECONDS debe ser un entero positivo");
}

function log(message) {
    print(`[indexes] ${message}`);
}

// IDs largos y fuera del rango de los registros heredados de PostgreSQL.
// Esto evita colisiones en la proyección técnica utilizada por el checkout.
database.contadores.updateOne(
    { _id: "carritos" },
    { $max: { valor: NumberLong("1000000000") } },
    { upsert: true }
);
database.contadores.updateOne(
    { _id: "carritoItems" },
    { $max: { valor: NumberLong("1000000000") } },
    { upsert: true }
);

// La numeracion tributaria no se puede repetir.
database.facturas.createIndex(
    { numeroFactura: 1 },
    { name: "ux_facturas_numeroFactura", unique: true }
);

// Historial de un cliente, desde la factura mas reciente.
// El campo es "cliente.clienteId" (snapshot embebido), NO un "clienteId"
// plano en la raiz: asi lo escribe CheckoutServicio y asi lo fija
// docs/03-checkout-transaccion.md 1.4. La version anterior indexaba
// "clienteId", campo que no existe en ningun documento de la coleccion, y
// por lo tanto no servia para el historial de pedidos que pide el punto 5.
const INDICE_HISTORIAL_OBSOLETO = "ix_facturas_clienteId_fechaEmision";
if (database.facturas.getIndexes().some((i) => i.name === INDICE_HISTORIAL_OBSOLETO)) {
    database.facturas.dropIndex(INDICE_HISTORIAL_OBSOLETO);
    log(`Indice obsoleto "${INDICE_HISTORIAL_OBSOLETO}" eliminado (indexaba un campo inexistente).`);
}
database.facturas.createIndex(
    { "cliente.clienteId": 1, fechaEmision: -1 },
    { name: "ix_facturas_cliente_fechaEmision" }
);

// Una orden documental origina una sola factura.
database.facturas.createIndex(
    { ordenId: 1 },
    { name: "ux_facturas_ordenId", unique: true }
);

// Una orden relacional (ordenes_entidad de Postgres) tiene a lo sumo UN
// espejo en `ordenes`. Parcial porque las ordenes documentales no llevan el
// campo, y sin el filtro todas ellas colisionarian entre si por null.
database.ordenes.createIndex(
    { ordenRelacionalId: 1 },
    {
        name: "ux_ordenes_ordenRelacionalId",
        unique: true,
        partialFilterExpression: { ordenRelacionalId: { $exists: true } }
    }
);

// ─── facturas_relacionales (flujo Lab 2, FacturaRepositorio) ─────────────
// Colección aparte de `facturas` porque el shape es incompatible y Mongo
// solo admite un $jsonSchema por coleccion (ver schema-validation.js).
// Los indices son los mismos conceptos, pero sobre los campos del shape
// relacional: clienteId plano en la raiz en vez de "cliente.clienteId".
database.facturas_relacionales.createIndex(
    { numeroFactura: 1 },
    { name: "ux_facturas_rel_numeroFactura", unique: true }
);
database.facturas_relacionales.createIndex(
    { clienteId: 1, fechaEmision: -1 },
    { name: "ix_facturas_rel_cliente_fechaEmision" }
);
database.facturas_relacionales.createIndex(
    { ordenId: 1 },
    { name: "ux_facturas_rel_ordenId", unique: true }
);

// Conserva la regla de un único carrito vigente por cliente.
database.carritos.createIndex(
    { clienteId: 1 },
    {
        name: "ux_carritos_vigentes_clienteId",
        unique: true,
        partialFilterExpression: { estado: { $in: ["ACTIVO", "ABANDONADO"] } }
    }
);

// Solo se eliminan carritos ABANDONADOS; ACTIVO y CONVERTIDO no expiran.
database.carritos.createIndex(
    { ultimaActividad: 1 },
    {
        name: "ttl_carritos_abandonados_ultimaActividad",
        expireAfterSeconds: TTL_CARRITO_SEGUNDOS,
        partialFilterExpression: { estado: "ABANDONADO" }
    }
);

// Índice de texto para búsqueda por nombre de producto.
// La colección `productos` de Mongo es la copia acotada que usa
// CheckoutServicio. El catálogo real está en Postgres (producto_entidad),
// pero se expone el índice aquí para demostrar el requerimiento de
// índices de texto del enunciado y para que búsquedas ad-hoc en mongosh
// (e.g. $text: { $search: "notebook" }) funcionen sin scan completo.
database.productos.createIndex(
    { nombre: "text" },
    { name: "text_productos_nombre", default_language: "spanish" }
);

log(`Indices listos. TTL de carritos abandonados: ${TTL_CARRITO_SEGUNDOS} segundos.`);
log(`facturas: ${JSON.stringify(database.facturas.getIndexes())}`);
log(`facturas_relacionales: ${JSON.stringify(database.facturas_relacionales.getIndexes())}`);
log(`carritos: ${JSON.stringify(database.carritos.getIndexes())}`);
log(`productos: ${JSON.stringify(database.productos.getIndexes())}`);
