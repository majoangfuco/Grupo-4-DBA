// ═══════════════════════════════════════════════════════════════
// rs-init.js  ·  Inicialización del Replica Set "rs0"
// Se ejecuta con mongosh contra mongo1 (conexión directa, autenticado
// con el usuario root creado por el entrypoint de la imagen oficial).
//
//   mongosh --host mongo1:27017 -u root -p ... --file /scripts/rs-init.js
//
// El script es IDEMPOTENTE: puede volver a ejecutarse tantas veces como
// sea necesario (docker compose up repetido, restart: on-failure, etc.).
// ═══════════════════════════════════════════════════════════════

const RS_NAME = process.env.MONGO_REPLICA_SET || "rs0";
const APP_DB = process.env.MONGO_DB || "b2b";
const APP_USER = process.env.MONGO_APP_USER || "b2b_app";
const APP_PASSWORD = process.env.MONGO_APP_PASSWORD || "b2b_app_pass";

function log(msg) {
    print(`[rs-init] ${msg}`);
}

function dormir(ms) {
    sleep(ms);
}

// ─── 1. rs.initiate() si el set todavía no existe ────────────────
let yaIniciado = false;
try {
    const estado = rs.status();
    yaIniciado = true;
    log(`El replica set "${estado.set}" ya estaba inicializado.`);
} catch (e) {
    // 94 = NotYetInitialized. Cualquier otro código es un error real.
    if (e.code !== 94) {
        throw e;
    }
    log(`Replica set no inicializado (code 94). Ejecutando rs.initiate()...`);
}

if (!yaIniciado) {
    // mongo1 lleva priority 2 para que sea siempre el PRIMARY elegido en
    // desarrollo: así la URI con directConnection=true (uso local, fuera de
    // Docker) apunta de forma estable al nodo que acepta escrituras.
    rs.initiate({
        _id: RS_NAME,
        members: [
            { _id: 0, host: "mongo1:27017", priority: 2 },
            { _id: 1, host: "mongo2:27017", priority: 1 }
        ]
    });
    log("rs.initiate() enviado.");
}

// ─── 2. Esperar a que este nodo sea PRIMARY ──────────────────────
// Sin un PRIMARY no se pueden crear usuarios ni abrir transacciones.
let esPrimary = false;
for (let intento = 1; intento <= 60; intento++) {
    try {
        const hello = db.hello();
        if (hello.isWritablePrimary) {
            esPrimary = true;
            log(`mongo1 es PRIMARY (intento ${intento}).`);
            break;
        }
        log(`Esperando elección de PRIMARY... (intento ${intento})`);
    } catch (e) {
        log(`Esperando a que el nodo responda: ${e.message} (intento ${intento})`);
    }
    dormir(1000);
}

if (!esPrimary) {
    throw new Error("mongo1 no llegó a ser PRIMARY tras 60 segundos.");
}

// ─── 3. Esperar a que mongo2 entre como SECONDARY ────────────────
// El laboratorio exige mínimo primario + secundario; si el secundario no
// sube, las transacciones con writeConcern "majority" quedan bloqueadas.
let haySecundario = false;
for (let intento = 1; intento <= 60; intento++) {
    const miembros = rs.status().members || [];
    haySecundario = miembros.some(m => m.stateStr === "SECONDARY");
    if (haySecundario) {
        log("mongo2 sincronizado como SECONDARY.");
        break;
    }
    log(`Esperando SECONDARY... (intento ${intento})`);
    dormir(1000);
}

if (!haySecundario) {
    throw new Error("mongo2 no alcanzó el estado SECONDARY tras 60 segundos.");
}

// ─── 4. Usuario de aplicación (el backend NO usa root) ───────────
// readWrite sobre la BD del proyecto  -> operaciones CRUD y transacciones.
// clusterMonitor sobre admin          -> permite que el endpoint de salud
//                                        del backend lea rs.status().
const admin = db.getSiblingDB("admin");
const existentes = admin.getUsers({ filter: { user: APP_USER } }).users || [];

const roles = [
    { role: "readWrite", db: APP_DB },
    { role: "dbAdmin", db: APP_DB },
    { role: "clusterMonitor", db: "admin" }
];

if (existentes.length === 0) {
    admin.createUser({ user: APP_USER, pwd: APP_PASSWORD, roles: roles });
    log(`Usuario de aplicación "${APP_USER}" creado.`);
} else {
    admin.updateUser(APP_USER, { pwd: APP_PASSWORD, roles: roles });
    log(`Usuario de aplicación "${APP_USER}" ya existía; credenciales y roles actualizados.`);
}

// ─── 5. Materializar la base de datos ────────────────────────────
// Mongo crea la BD de forma perezosa; se deja una colección de metadatos
// para que "show dbs" muestre b2b apenas termina el arranque.
const appDb = db.getSiblingDB(APP_DB);
appDb.getCollection("_infraestructura").updateOne(
    { _id: "replica_set" },
    {
        $set: {
            replicaSet: RS_NAME,
            miembros: ["mongo1:27017", "mongo2:27017"],
            inicializadoEn: new Date(),
            nota: "Documento de control creado por mongo/rs-init.js (Lab 3)."
        }
    },
    { upsert: true }
);

log("Replica set operativo. Transacciones multi-documento y change streams habilitados.");
