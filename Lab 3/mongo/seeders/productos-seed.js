// ═══════════════════════════════════════════════════════════════
// productos-seed.js · Copia acotada de producto_entidad para el checkout
//
// Esta colección Mongo "productos" NO es el catálogo maestro — ese sigue
// siendo producto_entidad en PostgreSQL, dueño de nombre/precio/stock/sku
// reales y consultado por ProductoServicio/ProductoRepositorio (y por
// CarritoMongoServicio.agregarItem al armar el snapshot del ítem del
// carrito). Esta copia existe únicamente porque CheckoutServicio necesita
// descontar stock con un updateOne condicional DENTRO de una transacción
// ACID de Mongo (session.withTransaction), y esa transacción no puede
// tocar Postgres en el mismo commit/abort — no hay forma de incluir una
// escritura relacional en un rollback de Mongo.
//
// Por eso: mismos `_id` que producto_ID de Postgres (no ObjectId nuevos),
// mismos nombre/precio/stock que la carga de backendB2B/init.sql al
// momento de escribir este script, para que la demo sea consistente entre
// ambos lados. Si el catálogo de Postgres cambia (nuevo producto, precio,
// reposición de stock), este seeder NO se actualiza solo — hay que
// volver a correrlo. No hay ETL automático entre los dos motores.
//
// Requiere el validador de "productos" ya aplicado
// (mongo/schema-validation.js) — créalo/actualízalo primero si no corriste
// ese script en este entorno todavía.
//
// Uso:
//   mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&directConnection=true" \
//       --file mongo/seeders/productos-seed.js
//
// Idempotente: usa upsert por _id, se puede correr tantas veces como
// haga falta (p. ej. para resetear el stock de la copia después de
// probar varios checkouts) sin duplicar documentos.
// ═══════════════════════════════════════════════════════════════

const DB_NAME = process.env.MONGO_DB || "b2b";
// `database` y no `db`: `const db = db.getSiblingDB(...)` sombrea el `db`
// global de mongosh y revienta con "Cannot access 'db' before initialization"
// (TDZ) antes de llegar a usarlo. Mismo patrón que mongo/indexes.js.
const database = db.getSiblingDB(DB_NAME);

function log(msg) {
    print(`[productos-seed] ${msg}`);
}

// cantidadMinimaB2B: producto_entidad (Postgres) no tiene este concepto.
// La fuente de verdad es configuracion_b2b_productos, gestionada por el
// Admin. En esta copia documental se conserva 1 solo como piso técnico de
// cantidad positiva, no como regla B2B especial.
const CANTIDAD_MINIMA_B2B_DEFAULT = 1;

// Sin mínimos precargados: si se quiere aplicar una regla B2B, el Admin
// debe configurarla explícitamente.
const CANTIDAD_MINIMA_B2B_OVERRIDES = {};

// _id, nombre, precioUnitario (SIN IVA, igual que producto_entidad.precio),
// stock — copiados 1:1 desde el INSERT INTO producto_entidad de
// backendB2B/init.sql (orden de inserción = producto_ID por ser SERIAL).
const productos = [
    { _id: 1, nombre: 'Notebook Empresarial Pro 15"', precioUnitario: 1200000.0, stock: 500 },
    { _id: 2, nombre: 'Monitor 27" 4K', precioUnitario: 350000.0, stock: 600 },
    { _id: 3, nombre: "Mini PC Corporativo", precioUnitario: 600000.0, stock: 300 },
    { _id: 4, nombre: "Silla Ergonómica Premium", precioUnitario: 180000.0, stock: 1000 },
    { _id: 5, nombre: "Escritorio Eléctrico Ajustable", precioUnitario: 450000.0, stock: 200 },
    { _id: 6, nombre: "Set Toners Impresora Láser", precioUnitario: 85000.0, stock: 2000 },
    { _id: 7, nombre: "Router Empresarial Wi-Fi 6", precioUnitario: 150000.0, stock: 400 },
    { _id: 8, nombre: "Switch Administrable 24 Puertos", precioUnitario: 250000.0, stock: 200 },
    { _id: 9, nombre: "Antivirus Corporativo (Lic. Anual)", precioUnitario: 300000.0, stock: 3000 },
    { _id: 10, nombre: "Suite Ofimática 365 (Lic. Anual)", precioUnitario: 120000.0, stock: 3000 },
    { _id: 11, nombre: "Servidor Rack 1U", precioUnitario: 2500000.0, stock: 80 },
    { _id: 12, nombre: "Disco Duro NAS 8TB", precioUnitario: 280000.0, stock: 350 },
    { _id: 13, nombre: "Cámara Videoconferencia 4K", precioUnitario: 450000.0, stock: 150 },
    { _id: 14, nombre: "Auriculares con Cancelación Ruido", precioUnitario: 85000.0, stock: 800 },
    { _id: 15, nombre: "Resmas de Papel A4 (Caja)", precioUnitario: 35000.0, stock: 4000 },
    { _id: 16, nombre: "Kit Cámaras de Seguridad CCTV", precioUnitario: 380000.0, stock: 100 },
    { _id: 17, nombre: "Control de Acceso Biométrico", precioUnitario: 120000.0, stock: 300 },
    { _id: 18, nombre: 'Tablet Corporativa 10"', precioUnitario: 220000.0, stock: 500 },
    { _id: 19, nombre: "Cajonera Metálica", precioUnitario: 95000.0, stock: 600 },
    { _id: 20, nombre: "Access Point Techo Wi-Fi 6", precioUnitario: 135000.0, stock: 350 },
    { _id: 21, nombre: "Teclado Mecánico Empresarial", precioUnitario: 65000.0, stock: 1000 },
    { _id: 22, nombre: "Mouse Inalámbrico Ergonómico", precioUnitario: 45000.0, stock: 1500 },
    { _id: 23, nombre: "Armario Archivo Metal 4 Cajones", precioUnitario: 210000.0, stock: 250 },
    { _id: 24, nombre: "UPS Online 1500VA", precioUnitario: 450000.0, stock: 80 },
    { _id: 25, nombre: "Licencia AutoCAD 2025 (Anual)", precioUnitario: 850000.0, stock: 500 },
].map(p => ({
    ...p,
    cantidadMinimaB2B: CANTIDAD_MINIMA_B2B_OVERRIDES[p._id] || CANTIDAD_MINIMA_B2B_DEFAULT,
}));

const coleccion = database.getCollection("productos");

const operaciones = productos.map(p => ({
    replaceOne: {
        filter: { _id: p._id },
        replacement: p,
        upsert: true
    }
}));

const resultado = coleccion.bulkWrite(operaciones, { ordered: true });

log(`Seed aplicado: ${resultado.upsertedCount} insertados, ${resultado.modifiedCount} actualizados `
    + `(de ${productos.length} productos totales).`);
log('Recordatorio: esto es una copia acotada para CheckoutServicio, no el catálogo — '
    + 'la fuente de verdad de nombre/precio/sku/categoría sigue siendo producto_entidad en Postgres.');
