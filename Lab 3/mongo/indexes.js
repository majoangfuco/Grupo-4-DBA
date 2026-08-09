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

// La orden relacional puede originar una sola factura documental.
database.facturas.createIndex(
    { ordenId: 1 },
    { name: "ux_facturas_ordenId", unique: true }
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

log(`Indices listos. TTL de carritos abandonados: ${TTL_CARRITO_SEGUNDOS} segundos.`);
log(`facturas: ${JSON.stringify(database.facturas.getIndexes())}`);
log(`carritos: ${JSON.stringify(database.carritos.getIndexes())}`);
