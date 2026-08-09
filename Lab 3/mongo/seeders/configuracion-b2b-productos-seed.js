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
// CarritoMongoServicio.obtenerCantidadMinima devuelve 1 por defecto
// (MINIMO_POR_DEFECTO) sin necesidad de que exista el documento acá — por
// eso este seeder SOLO carga los productos con mínimo distinto de 1. No
// tiene sentido poblar los 25 productos con cantidadMinimaB2B: 1, sería
// idéntico a no tener el documento.
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
const db = db.getSiblingDB(DB_NAME);

function log(msg) {
    print(`[configuracion-b2b-productos-seed] ${msg}`);
}

// Debe calcar CANTIDAD_MINIMA_B2B_OVERRIDES de productos-seed.js.
const CANTIDAD_MINIMA_B2B_OVERRIDES = {
    6: 5,   // Set Toners Impresora Láser — mínimo 5 packs por pedido B2B
    15: 10, // Resmas de Papel A4 (Caja) — mínimo 10 cajas por pedido B2B
    22: 10, // Mouse Inalámbrico Ergonómico — mínimo 10 unidades por pedido B2B
};

const coleccion = db.getCollection("configuracion_b2b_productos");

const operaciones = Object.entries(CANTIDAD_MINIMA_B2B_OVERRIDES).map(([productoId, cantidadMinimaB2B]) => ({
    replaceOne: {
        filter: { productoId: Number(productoId) },
        replacement: { productoId: Number(productoId), cantidadMinimaB2B },
        upsert: true
    }
}));

const resultado = coleccion.bulkWrite(operaciones, { ordered: true });

log(`Seed aplicado: ${resultado.upsertedCount} insertados, ${resultado.modifiedCount} actualizados `
    + `(de ${operaciones.length} configuraciones totales).`);
log('Recordatorio: productos sin documento acá usan el default de CarritoMongoServicio.MINIMO_POR_DEFECTO (1).');
