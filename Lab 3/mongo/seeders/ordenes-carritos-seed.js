// ═══════════════════════════════════════════════════════════════
// ordenes-carritos-seed.js · Datos de demo para Tareas 4 y 6
//
// Propósito: dar contenido a los dos endpoints Mongo del Lab 3
// que dependen de datos reales para no devolver vacío en la demo:
//
//   Tarea 4 — GET /api/reportes/mongo/volumen-proyectado
//     Lee la colección `carritos` en estado ACTIVO y agrupa por
//     cliente y categoría. Sin carritos ACTIVO con items, el
//     pipeline de $group/$bucket devuelve [].
//
//   Tarea 6 — GET /api/reportes/mongo/productos-mas-vendidos
//     Lee la colección `ordenes` en estado CONFIRMADA. Sin
//     órdenes CONFIRMADAS, la vista materializada queda vacía.
//
// Shape de `ordenes`:
//   Idéntico al que escribe CheckoutServicio.ejecutarCheckout():
//   _id (ObjectId), numeroOrden, clienteId (Long), cliente{},
//   carritoId (Long), estado, items[], totalNeto, iva, total,
//   fechaOrden, facturaId (ObjectId), fechaConfirmacion.
//
// Shape de `carritos` (ítems):
//   Idéntico al que valida schema-validation.js: cada ítem debe
//   tener itemId, productoId, cantidad, precioUnitario,
//   stockDisponibleAlAgregar + subtotal (campo libre, no required
//   por el validador pero leído por el pipeline de Tarea 4).
//   El validador $expr exige cantidad ≤ stockDisponibleAlAgregar,
//   así que todos los ítems cumplen esa restricción.
//
// Idempotente:
//   - ordenes:  replaceOne por _id (upsert); los _id son strings
//     de ObjectId fijos para que el seed no cree duplicados al
//     correrlo dos veces.
//   - carritos: replaceOne por _id fijo (1000000013..1000000017),
//     limpiando antes
//     cualquier documento previo con el mismo clienteId+ACTIVO/
//     ABANDONADO pero _id distinto (respeta el índice único partial
//     del punto 5 y evita el ClassCastException del backend si un
//     _id quedó como ObjectId de una corrida anterior).
//
// Requiere: schema-validation.js e indexes.js ya corridos.
//
// Uso:
//   mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&replicaSet=rs0" \
//       --file mongo/seeders/ordenes-carritos-seed.js
// ═══════════════════════════════════════════════════════════════

const DB_NAME = process.env.MONGO_DB || "b2b";
const database = db.getSiblingDB(DB_NAME);

function log(msg) {
    print(`[ordenes-carritos-seed] ${msg}`);
}

// ─── Catálogo de referencia (subconjunto de productos-seed.js) ──
// Se usa solo para calcular subtotales; el campo nombre/precio viene
// del mismo seeder de productos para que los datos sean coherentes.
const CATALOGO = {
    1:  { nombre: 'Notebook Empresarial Pro 15"', precio: 1200000, categoria: "Computación" },
    2:  { nombre: 'Monitor 27" 4K',               precio: 350000,  categoria: "Computación" },
    3:  { nombre: "Mini PC Corporativo",           precio: 600000,  categoria: "Computación" },
    4:  { nombre: "Silla Ergonómica Premium",      precio: 180000,  categoria: "Mobiliario" },
    5:  { nombre: "Escritorio Eléctrico Ajustable",precio: 450000,  categoria: "Mobiliario" },
    6:  { nombre: "Set Toners Impresora Láser",    precio: 85000,   categoria: "Insumos" },
    7:  { nombre: "Router Empresarial Wi-Fi 6",    precio: 150000,  categoria: "Redes" },
    9:  { nombre: "Antivirus Corporativo (Lic. Anual)", precio: 300000, categoria: "Software" },
    10: { nombre: "Suite Ofimática 365 (Lic. Anual)", precio: 120000,  categoria: "Software" },
    11: { nombre: "Servidor Rack 1U",              precio: 2500000, categoria: "Servidores" },
    12: { nombre: "Disco Duro NAS 8TB",            precio: 280000,  categoria: "Almacenamiento" },
    13: { nombre: "Cámara Videoconferencia 4K",    precio: 450000,  categoria: "Audiovisual" },
    14: { nombre: "Auriculares con Cancelación Ruido", precio: 85000, categoria: "Audiovisual" },
    15: { nombre: "Resmas de Papel A4 (Caja)",     precio: 35000,   categoria: "Insumos" },
    18: { nombre: 'Tablet Corporativa 10"',        precio: 220000,  categoria: "Computación" },
    21: { nombre: "Teclado Mecánico Empresarial",  precio: 65000,   categoria: "Periféricos" },
    22: { nombre: "Mouse Inalámbrico Ergonómico",  precio: 45000,   categoria: "Periféricos" },
    25: { nombre: "Licencia AutoCAD 2025 (Anual)", precio: 850000,  categoria: "Software" },
};

const IVA = 0.19;

// Helper: construye un ítem de orden a partir del catálogo
function itemOrden(productoId, cantidad) {
    const p = CATALOGO[productoId];
    const subtotal = NumberDecimal((p.precio * cantidad).toFixed(2));
    return {
        productoId: NumberLong(productoId),
        nombreProducto: p.nombre,
        cantidad: NumberLong(cantidad),
        precioUnitario: NumberDecimal(p.precio.toFixed(2)),
        subtotal,
    };
}

// Helper: construye un ítem de carrito (agrega campos del validador)
function itemCarrito(itemId, productoId, cantidad, stockDisponible) {
    const p = CATALOGO[productoId];
    return {
        itemId: NumberLong(itemId),
        productoId: NumberLong(productoId),
        sku: `SKU-${String(productoId).padStart(4, "0")}`,
        nombreProducto: p.nombre,
        categoriaNombre: p.categoria,
        cantidad: NumberLong(cantidad),
        // OJO: a diferencia de `ordenes` (donde CheckoutServicio sí usa
        // Decimal128), el backend real escribe carritos.items.precioUnitario
        // como double llano (ver CarritoMongoRepositorio + la clase POJO
        // ItemCarritoMongoEntidad, campo `Double precioUnitario`). El driver
        // de Mongo rechaza decodificar Decimal128 -> Double por riesgo de
        // pérdida de precisión, así que sembrarlo como NumberDecimal rompía
        // con CodecConfigurationException cualquier lectura de ese carrito
        // (agregar/actualizar/eliminar un ítem) en cuanto tocaba un item
        // sembrado por este script. Double(...) es necesario y no basta con
        // un literal JS: mongosh serializa un número entero sin decimales
        // como BSON int32 por defecto, lo que el $jsonSchema (bsonType:
        // ["double","decimal"]) rechaza igual que rechazaba "decimal" antes.
        precioUnitario: Double(p.precio),
        cantidadMinimaB2B: NumberLong(1),
        stockDisponibleAlAgregar: NumberLong(stockDisponible),
        subtotal: NumberDecimal((p.precio * cantidad).toFixed(2)),
    };
}

// Helper: calcula totales de una lista de items de orden
function calcularTotales(items) {
    const totalNeto = items.reduce((acc, it) => {
        return acc + parseFloat(it.subtotal.toString());
    }, 0);
    const iva = Math.round(totalNeto * IVA * 100) / 100;
    const total = Math.round((totalNeto + iva) * 100) / 100;
    return {
        totalNeto: NumberDecimal(totalNeto.toFixed(2)),
        iva:       NumberDecimal(iva.toFixed(2)),
        total:     NumberDecimal(total.toFixed(2)),
    };
}

// ═══════════════════════════════════════════════════════════════
// 1. ÓRDENES CONFIRMADAS (Tarea 6 — Change Streams / $merge)
//
//    12 órdenes confirmadas de 5 clientes distintos, distribuidas
//    en distintas fechas para demostrar el campo fechaConfirmacion
//    y el rollup de ultimaVentaEn en la vista materializada.
// ═══════════════════════════════════════════════════════════════

const ORDENES = [
    // ─── Cliente 1 (TecnoDistrib SpA, 11.111.111-1) ─────────────
    {
        ordenIdHex: "aaaaaaaaaaaaaaaaaaaaaaaa",
        numeroOrden: "ORD-2026-000001",
        clienteId: 1,
        razonSocial: "TecnoDistrib SpA",
        rutEmpresa: "11.111.111-1",
        direccionEnvio: "Av. Providencia 1234, Santiago",
        carritoId: NumberLong("1000000001"),
        fechaOrden: new Date("2026-01-15T10:00:00Z"),
        fechaConfirmacion: new Date("2026-01-15T10:05:00Z"),
        items: [
            itemOrden(1,  5),   // 5 × Notebook Pro 15"
            itemOrden(2,  10),  // 10 × Monitor 27" 4K
            itemOrden(21, 20),  // 20 × Teclado Mecánico
            itemOrden(22, 20),  // 20 × Mouse Inalámbrico
        ],
    },
    {
        ordenIdHex: "aaaaaaaaaaaaaaaaaaaaaaab",
        numeroOrden: "ORD-2026-000002",
        clienteId: 1,
        razonSocial: "TecnoDistrib SpA",
        rutEmpresa: "11.111.111-1",
        direccionEnvio: "Av. Providencia 1234, Santiago",
        carritoId: NumberLong("1000000002"),
        fechaOrden: new Date("2026-03-10T09:00:00Z"),
        fechaConfirmacion: new Date("2026-03-10T09:10:00Z"),
        items: [
            itemOrden(9,  10),  // 10 × Antivirus Corporativo
            itemOrden(10, 15),  // 15 × Suite Ofimática 365
            itemOrden(25, 5),   // 5 × Licencia AutoCAD
        ],
    },
    // ─── Cliente 2 (Oficinas del Sur Ltda., 22.222.222-2) ────────
    {
        ordenIdHex: "bbbbbbbbbbbbbbbbbbbbbbbb",
        numeroOrden: "ORD-2026-000003",
        clienteId: 2,
        razonSocial: "Oficinas del Sur Ltda.",
        rutEmpresa: "22.222.222-2",
        direccionEnvio: "Calle Lota 2345, Concepción",
        carritoId: NumberLong("1000000003"),
        fechaOrden: new Date("2026-02-01T11:00:00Z"),
        fechaConfirmacion: new Date("2026-02-01T11:08:00Z"),
        items: [
            itemOrden(4,  20),  // 20 × Silla Ergonómica
            itemOrden(5,  10),  // 10 × Escritorio Eléctrico
            // item 19 removido para evitar crash al evaluar CATALOGO[19].precio
        ],
    },
    {
        ordenIdHex: "bbbbbbbbbbbbbbbbbbbbbbbc",
        numeroOrden: "ORD-2026-000004",
        clienteId: 2,
        razonSocial: "Oficinas del Sur Ltda.",
        rutEmpresa: "22.222.222-2",
        direccionEnvio: "Calle Lota 2345, Concepción",
        carritoId: NumberLong("1000000004"),
        fechaOrden: new Date("2026-05-20T14:00:00Z"),
        fechaConfirmacion: new Date("2026-05-20T14:15:00Z"),
        items: [
            itemOrden(4,  30),  // 30 × Silla Ergonómica
            itemOrden(6,  50),  // 50 × Set Toners
            itemOrden(15, 100), // 100 × Resmas Papel A4
        ],
    },
    // ─── Cliente 3 (Redes Corp S.A., 33.333.333-3) ───────────────
    {
        ordenIdHex: "cccccccccccccccccccccccc",
        numeroOrden: "ORD-2026-000005",
        clienteId: 3,
        razonSocial: "Redes Corp S.A.",
        rutEmpresa: "33.333.333-3",
        direccionEnvio: "Av. El Bosque Norte 500, Vitacura",
        carritoId: NumberLong("1000000005"),
        fechaOrden: new Date("2026-02-15T08:30:00Z"),
        fechaConfirmacion: new Date("2026-02-15T08:40:00Z"),
        items: [
            itemOrden(7,  20),  // 20 × Router Wi-Fi 6
            itemOrden(3,  10),  // 10 × Mini PC
            itemOrden(12, 15),  // 15 × Disco NAS 8TB
        ],
    },
    {
        ordenIdHex: "cccccccccccccccccccccccd",
        numeroOrden: "ORD-2026-000006",
        clienteId: 3,
        razonSocial: "Redes Corp S.A.",
        rutEmpresa: "33.333.333-3",
        direccionEnvio: "Av. El Bosque Norte 500, Vitacura",
        carritoId: NumberLong("1000000006"),
        fechaOrden: new Date("2026-04-05T10:00:00Z"),
        fechaConfirmacion: new Date("2026-04-05T10:12:00Z"),
        items: [
            itemOrden(11, 2),   // 2 × Servidor Rack 1U
            itemOrden(7,  30),  // 30 × Router Wi-Fi 6
            itemOrden(9,  5),   // 5 × Antivirus
        ],
    },
    {
        ordenIdHex: "cccccccccccccccccccccce0",
        numeroOrden: "ORD-2026-000007",
        clienteId: 3,
        razonSocial: "Redes Corp S.A.",
        rutEmpresa: "33.333.333-3",
        direccionEnvio: "Av. El Bosque Norte 500, Vitacura",
        carritoId: NumberLong("1000000007"),
        fechaOrden: new Date("2026-06-10T09:00:00Z"),
        fechaConfirmacion: new Date("2026-06-10T09:20:00Z"),
        items: [
            itemOrden(7,  25),  // 25 × Router Wi-Fi 6
            itemOrden(12, 20),  // 20 × Disco NAS 8TB
        ],
    },
    // ─── Cliente 4 (AV Solutions Ltda., 44.444.444-4) ────────────
    {
        ordenIdHex: "dddddddddddddddddddddddd",
        numeroOrden: "ORD-2026-000008",
        clienteId: 4,
        razonSocial: "AV Solutions Ltda.",
        rutEmpresa: "44.444.444-4",
        direccionEnvio: "Blanco Encalada 890, Valparaíso",
        carritoId: NumberLong("1000000008"),
        fechaOrden: new Date("2026-03-01T13:00:00Z"),
        fechaConfirmacion: new Date("2026-03-01T13:05:00Z"),
        items: [
            itemOrden(13, 10),  // 10 × Cámara Videoconferencia
            itemOrden(14, 30),  // 30 × Auriculares ANC
            itemOrden(18, 15),  // 15 × Tablet Corporativa
        ],
    },
    {
        ordenIdHex: "ddddddddddddddddddddddde",
        numeroOrden: "ORD-2026-000009",
        clienteId: 4,
        razonSocial: "AV Solutions Ltda.",
        rutEmpresa: "44.444.444-4",
        direccionEnvio: "Blanco Encalada 890, Valparaíso",
        carritoId: NumberLong("1000000009"),
        fechaOrden: new Date("2026-06-25T15:00:00Z"),
        fechaConfirmacion: new Date("2026-06-25T15:10:00Z"),
        items: [
            itemOrden(13, 5),   // 5 × Cámara Videoconferencia
            itemOrden(14, 50),  // 50 × Auriculares ANC
        ],
    },
    // ─── Cliente 5 (Software Total S.A., 55.555.555-5) ───────────
    {
        ordenIdHex: "eeeeeeeeeeeeeeeeeeeeeeee",
        numeroOrden: "ORD-2026-000010",
        clienteId: 5,
        razonSocial: "Software Total S.A.",
        rutEmpresa: "55.555.555-5",
        direccionEnvio: "Av. Apoquindo 3000, Las Condes",
        carritoId: NumberLong("1000000010"),
        fechaOrden: new Date("2026-04-20T16:00:00Z"),
        fechaConfirmacion: new Date("2026-04-20T16:08:00Z"),
        items: [
            itemOrden(25, 10),  // 10 × Licencia AutoCAD
            itemOrden(10, 50),  // 50 × Suite Ofimática
            itemOrden(9,  20),  // 20 × Antivirus
        ],
    },
    {
        ordenIdHex: "eeeeeeeeeeeeeeeeeeeeeeef",
        numeroOrden: "ORD-2026-000011",
        clienteId: 5,
        razonSocial: "Software Total S.A.",
        rutEmpresa: "55.555.555-5",
        direccionEnvio: "Av. Apoquindo 3000, Las Condes",
        carritoId: NumberLong("1000000011"),
        fechaOrden: new Date("2026-07-05T10:00:00Z"),
        fechaConfirmacion: new Date("2026-07-05T10:15:00Z"),
        items: [
            itemOrden(1,  8),   // 8 × Notebook Pro 15"
            itemOrden(2,  8),   // 8 × Monitor 27" 4K
            itemOrden(10, 30),  // 30 × Suite Ofimática
            itemOrden(25, 8),   // 8 × Licencia AutoCAD
        ],
    },
    {
        ordenIdHex: "eeeeeeeeeeeeeeeeeeeeeef0",
        numeroOrden: "ORD-2026-000012",
        clienteId: 5,
        razonSocial: "Software Total S.A.",
        rutEmpresa: "55.555.555-5",
        direccionEnvio: "Av. Apoquindo 3000, Las Condes",
        carritoId: NumberLong("1000000012"),
        fechaOrden: new Date("2026-08-01T09:00:00Z"),
        fechaConfirmacion: new Date("2026-08-01T09:30:00Z"),
        items: [
            itemOrden(25, 5),   // 5 × Licencia AutoCAD
            itemOrden(9,  15),  // 15 × Antivirus
        ],
    },
];

// ─── Corrección del ítem 19 en la orden bbbbbbbbbbbbbbbbbbbbbbbb ─
// productoId 19 no está en el CATALOGO reducido → reemplazamos con
// items que sí están para no enviar items undefined al seeder.
ORDENES[2].items = [
    itemOrden(4,  20),  // 20 × Silla Ergonómica
    itemOrden(5,  10),  // 10 × Escritorio Eléctrico
    itemOrden(6,  30),  // 30 × Set Toners (reemplaza 19)
];

log("Sembrando colección `ordenes` (12 órdenes CONFIRMADAS)...");

const colOrdenes = database.getCollection("ordenes");
let insertadasO = 0, actualizadasO = 0;

for (const o of ORDENES) {
    const { ordenIdHex, numeroOrden, clienteId, razonSocial, rutEmpresa,
            direccionEnvio, carritoId, fechaOrden, fechaConfirmacion, items } = o;

    const totales = calcularTotales(items);
    const ordenId  = new ObjectId(ordenIdHex);
    // facturaId derivado: misma base hex + "ff" al final, para no colisionar
    const facturaId = new ObjectId(ordenIdHex.slice(0, 22) + "ff");

    const doc = {
        _id: ordenId,
        numeroOrden,
        clienteId: NumberLong(clienteId),
        cliente: { razonSocial, rutEmpresa, direccionEnvio },
        carritoId,
        estado: "CONFIRMADA",
        items,
        totalNeto: totales.totalNeto,
        iva:       totales.iva,
        total:     totales.total,
        fechaOrden,
        fechaConfirmacion,
        facturaId,
    };

    const res = colOrdenes.replaceOne({ _id: ordenId }, doc, { upsert: true });
    if (res.upsertedCount) insertadasO++;
    else if (res.modifiedCount) actualizadasO++;
}

log(`ordenes: ${insertadasO} insertadas, ${actualizadasO} actualizadas (${ORDENES.length} totales).`);

// ═══════════════════════════════════════════════════════════════
// 2. CARRITOS ACTIVOS (Tarea 4 — Aggregation Pipeline)
//
//    5 carritos en estado ACTIVO, uno por cliente, con ítems
//    que incluyen categoriaNombre (campo libre que lee el pipeline)
//    y que cumplen TODOS los requisitos del validador:
//      - cantidad ≤ stockDisponibleAlAgregar  ✓
//      - cantidad ≥ cantidadMinimaB2B         ✓
//      - ultimaActividad (Date, para TTL)     ✓
//
//    Los subtotales variarán entre los 3 buckets para que la demo
//    muestre los tres niveles (BAJO/MEDIO/ALTO).
//
//    IDs: los `_id` van en el rango reservado 1000000000+, NUNCA en
//    el rango bajo de PostgreSQL. `solicitarOrdenAtomica` proyecta el
//    carrito Mongo en `carrito_entidad` con el MISMO id
//    (OrdenesRepositorio.proyectarCarritoParaCheckout), así que un
//    `_id` chico choca contra un carrito heredado del Lab 2: el
//    ON CONFLICT (carrito_id) DO UPDATE se lleva por delante un
//    carrito PAGADO ajeno, le cambia el dueño y lo vuelve ACTIVO,
//    reventando además el índice parcial ux_carrito_activo_abandonado.
//    Es la misma invariante que fija indexes.js al inicializar el
//    contador `carritos` en 1000000000.
//
//    Reparto del rango: 1000000001..1000000012 ya los ocupan los
//    carritos convertidos que referencian las 12 órdenes de arriba,
//    así que los 5 carritos ACTIVOS siguen en 1000000013..1000000017.
// ═══════════════════════════════════════════════════════════════

const HOY = new Date();

const CARRITOS = [
    // ── Cliente 1 — bucket ALTO (subtotal por categoría ≥ 200.000) ──
    {
        _id: 1000000013,
        clienteId: NumberLong(1),
        estado: "ACTIVO",
        ultimaActividad: HOY,
        items: [
            itemCarrito(2000000001, 1,  5,  500),  // 5 × Notebook  → $6.000.000 (Computación)
            itemCarrito(2000000002, 2,  10, 600),  // 10 × Monitor  → $3.500.000 (Computación)
            itemCarrito(2000000003, 9,  8,  3000), // 8 × Antivirus → $2.400.000 (Software)
        ],
    },
    // ── Cliente 2 — bucket MEDIO (50.000–200.000) ──────────────────
    {
        _id: 1000000014,
        clienteId: NumberLong(2),
        estado: "ACTIVO",
        ultimaActividad: HOY,
        items: [
            itemCarrito(2000000010, 6,  5,  2000), // 5 × Toners    → $425.000 (Insumos)
            itemCarrito(2000000011, 15, 10, 4000), // 10 × Resmas   → $350.000 (Insumos)
            itemCarrito(2000000012, 21, 5,  1000), // 5 × Teclado   → $325.000 (Periféricos)
        ],
    },
    // ── Cliente 3 — bucket BAJO (< 50.000) ─────────────────────────
    {
        _id: 1000000015,
        clienteId: NumberLong(3),
        estado: "ACTIVO",
        ultimaActividad: HOY,
        items: [
            itemCarrito(2000000020, 22, 1, 1500),  // 1 × Mouse     → $45.000 (Periféricos)
            itemCarrito(2000000021, 14, 1, 800),   // 1 × Auricular → $85.000 (Audiovisual)
        ],
    },
    // ── Cliente 4 — bucket ALTO (varios buckets por categoría) ─────
    {
        _id: 1000000016,
        clienteId: NumberLong(4),
        estado: "ACTIVO",
        ultimaActividad: HOY,
        items: [
            itemCarrito(2000000030, 13, 5,  150),  // 5 × Cámara      → $2.250.000 (Audiovisual)
            itemCarrito(2000000031, 18, 10, 500),  // 10 × Tablet     → $2.200.000 (Computación)
            itemCarrito(2000000032, 4,  15, 1000), // 15 × Silla      → $2.700.000 (Mobiliario)
        ],
    },
    // ── Cliente 5 — bucket ALTO (software) ─────────────────────────
    {
        _id: 1000000017,
        clienteId: NumberLong(5),
        estado: "ACTIVO",
        ultimaActividad: HOY,
        items: [
            itemCarrito(2000000040, 25, 3, 500),   // 3 × AutoCAD     → $2.550.000 (Software)
            itemCarrito(2000000041, 10, 20,3000),  // 20 × Ofimática  → $2.400.000 (Software)
            itemCarrito(2000000042, 9,  10,3000),  // 10 × Antivirus  → $3.000.000 (Software)
        ],
    },
];

log("Sembrando colección `carritos` (5 carritos ACTIVOS)...");

const colCarritos = database.getCollection("carritos");
let insertadasC = 0, actualizadasC = 0;

for (const c of CARRITOS) {
    // IMPORTANTE: el backend (CarritoRepositorio.mapear) asume que
    // `_id` es siempre Number (esquema de contador Long, ver
    // CarritoRepositorio.crearCarrito), igual que las órdenes reales
    // creadas por la app. Por eso el seed fija `_id` explícito en vez
    // de dejar que Mongo autogenere un ObjectId: un carrito con _id
    // ObjectId rompe con ClassCastException cualquier lectura de ese
    // carrito (GET /carrito, agregar producto, etc.).
    //
    // Como `_id` es inmutable, replaceOne por _id fallaría si ya
    // existe un documento con el mismo clienteId+estado pero un _id
    // distinto (p. ej. una corrida antigua de este seed que sí dejó
    // un ObjectId). Se limpia ese caso primero para que la carga siga
    // siendo idempotente sin importar el historial de la base.
    colCarritos.deleteMany({
        clienteId: c.clienteId,
        estado: { $in: ["ACTIVO", "ABANDONADO"] },
        _id: { $ne: c._id },
    });
    const res = colCarritos.replaceOne({ _id: c._id }, c, { upsert: true });
    if (res.upsertedCount) insertadasC++;
    else if (res.modifiedCount) actualizadasC++;
}

log(`carritos: ${insertadasC} insertados, ${actualizadasC} actualizados (${CARRITOS.length} totales).`);

// El contador que usa CarritoRepositorio.siguienteId() tiene que quedar
// por encima de todo lo sembrado; si no, el primer carrito que cree un
// usuario real reutiliza un `_id` de este seed. $max lo deja idempotente
// y nunca lo hace retroceder si la app ya avanzó más allá.
const MAX_CARRITO_SEMBRADO = CARRITOS.reduce((max, c) => Math.max(max, c._id), 0);
database.getCollection("contadores").updateOne(
    { _id: "carritos" },
    { $max: { valor: NumberLong(String(MAX_CARRITO_SEMBRADO)) } },
    { upsert: true }
);
log(`contador \`carritos\` asegurado en >= ${MAX_CARRITO_SEMBRADO}.`);

// ═══════════════════════════════════════════════════════════════
// 3. BACKFILL de la vista materializada
//
//    Se dispara el mismo pipeline $merge que usa el worker de
//    change streams para que `productos_mas_vendidos` quede
//    poblada al instante, sin tener que esperar a que el worker
//    procese eventos anteriores.
// ═══════════════════════════════════════════════════════════════
log("Ejecutando backfill de productos_mas_vendidos ($merge)...");

const pipelineMerge = [
    { $match: { estado: "CONFIRMADA" } },
    { $unwind: "$items" },
    { $sort: { fechaOrden: 1 } },
    {
        $group: {
            _id: "$items.productoId",
            nombreProducto:    { $last:  "$items.nombreProducto" },
            unidadesVendidas:  { $sum:   "$items.cantidad" },
            montoTotalVendido: { $sum:   "$items.subtotal" },
            ordenesConfirmadas:{ $sum:   1 },
            ultimaVentaEn:     { $max:   { $ifNull: ["$fechaConfirmacion", "$fechaOrden"] } },
        },
    },
    { $sort: { unidadesVendidas: -1 } },
    {
        $project: {
            _id: 1,
            productoId:         "$_id",
            nombreProducto:     1,
            unidadesVendidas:   1,
            montoTotalVendido:  1,
            ordenesConfirmadas: 1,
            ultimaVentaEn:      1,
            actualizadoEn:      "$$NOW",
        },
    },
    {
        $merge: {
            into: "productos_mas_vendidos",
            on: "_id",
            whenMatched:    "replace",
            whenNotMatched: "insert",
        },
    },
];

database.ordenes.aggregate(pipelineMerge).toArray();

const totalMV = database.productos_mas_vendidos.countDocuments({});
log(`Backfill completado: ${totalMV} producto(s) en productos_mas_vendidos.`);

// ─── Resumen final ───────────────────────────────────────────────
log("─".repeat(60));
log("Top 5 productos más vendidos:");
database.productos_mas_vendidos
    .find({}, { nombreProducto: 1, unidadesVendidas: 1, montoTotalVendido: 1 })
    .sort({ unidadesVendidas: -1 })
    .limit(5)
    .toArray()
    .forEach((p, i) => {
        log(`  ${i + 1}. ${p.nombreProducto} — ${p.unidadesVendidas} uds`);
    });
log("─".repeat(60));
log("Seed completado. Tarea 4 (pipeline) y Tarea 6 (change streams) tienen datos.");
