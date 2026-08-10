// ═══════════════════════════════════════════════════════════════
// generar-reservas-stock-postgres.js
//
// Por qué existe: la reserva de stock real vive en PostgreSQL
// (producto_entidad.stock_reservado, vía los procedimientos
// reservar_stock/liberar_stock — ver backendB2B/init.sql). El flujo real
// de "agregar al carrito" la actualiza en cada llamada
// (CarritoProductoServicio.reservarStockValidado -> CarritoProductoRepositorio
// .reservarStock -> CALL reservar_stock). Los carritos de demo que siembra
// ordenes-carritos-seed.js se insertan DIRECTO en la colección `carritos`
// de Mongo, sin pasar por ese código Java — así que sin este script la
// reserva de Postgres queda en 0 para esos productos, y cualquier
// operación que dependa de "liberar" esa reserva (borrar un ítem del
// carrito, solicitar la orden) falla con "Stock reservado insuficiente".
//
// Qué hace: lee EN VIVO la colección `carritos` (estado ACTIVO/ABANDONADO
// — los únicos que representan stock reservado de verdad, mismo criterio
// que usa el backend) y agrupa la cantidad total por producto. Por cada
// producto imprime una línea SQL:
//     INSERT INTO _reservas_objetivo (producto_id, cantidad) VALUES (...);
//
// Deliberadamente NO lee el array CARRITOS de ordenes-carritos-seed.js ni
// ningún valor hardcodeado: lee lo que TERMINÓ escrito en Mongo. Así este
// script nunca puede desincronizarse de la siembra real, sin importar si
// alguien cambia cantidades allá — no hay dos fuentes de verdad que
// mantener sincronizadas a mano.
//
// IMPORTANTE — este script NO imprime nada más que esas líneas SQL. El
// docker-compose (servicio `mongo-init`, ver docker-compose.yml) captura
// este stdout completo a un archivo y lo concatena entre
// mongo/seeders/postgres/reservar-stock-demo.header.sql y
// reservar-stock-demo.footer.sql antes de correrlo con psql (servicio
// `postgres-stock-seed`). Cualquier log()/print() adicional acá rompería
// ese archivo generado.
//
// Uso (fuera de docker-compose, para depurar a mano):
//   mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&replicaSet=rs0" \
//       --quiet --file mongo/seeders/generar-reservas-stock-postgres.js
// ═══════════════════════════════════════════════════════════════

const DB_NAME = process.env.MONGO_DB || "b2b";
const database = db.getSiblingDB(DB_NAME);

const filas = database.carritos.aggregate([
    // ACTIVO/ABANDONADO: mismo criterio que usa el backend real para "hay
    // stock reservado" — un carrito CONVERTIDO ya pasó por el checkout y
    // su stock dejó de estar "reservado" (o se consumió, o se liberó).
    { $match: { estado: { $in: ["ACTIVO", "ABANDONADO"] } } },
    { $unwind: "$items" },
    {
        $group: {
            _id: "$items.productoId",
            cantidad: { $sum: "$items.cantidad" },
        },
    },
    { $sort: { _id: 1 } },
]).toArray();

for (const fila of filas) {
    const productoId = Number(fila._id);
    const cantidad = Number(fila.cantidad);
    print(`INSERT INTO _reservas_objetivo (producto_id, cantidad) VALUES (${productoId}, ${cantidad});`);
}
