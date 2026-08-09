/**
 * Tarea 4: Aggregation Pipeline - Volumen de ventas proyectado
 * 
 * Calcula el volumen de ventas proyectado por cliente y categoría de producto 
 * en carritos activos. Clasifica los resultados en tres niveles de venta (BAJO, MEDIO, ALTO)
 * mediante $bucket, y los ordena con $sort.
 * 
 * Uso: mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&replicaSet=rs0" mongo/aggregation-pipeline.js
 */

const DB_NAME = process.env.MONGO_DB || "b2b";
const db = db.getSiblingDB(DB_NAME);

print("\n--- Ejecutando Aggregation Pipeline: Volumen de Ventas Proyectado ---\n");

const pipeline = [
    // 1. Filtrar solo los carritos activos (ventas proyectadas, aún no confirmadas)
    {
        $match: {
            estado: "ACTIVO"
        }
    },
    // 2. Desenrollar los ítems para acceder a sus categorías
    {
        $unwind: "$items"
    },
    // 3. Agrupar por cliente y categoría, y sumarizar el subtotal
    {
        $group: {
            _id: {
                clienteId: "$clienteId",
                categoriaNombre: "$items.categoriaNombre"
            },
            volumenProyectado: { $sum: "$items.subtotal" }
        }
    },
    // 4. Ordenar las proyecciones de mayor a menor (esto afectará el orden dentro de los buckets)
    {
        $sort: { volumenProyectado: -1 }
    },
    // 5. Agrupar (bucket) los resultados en rangos de volumen para análisis
    {
        $bucket: {
            groupBy: "$volumenProyectado",
            boundaries: [0, 50000, 200000], // Limites: [0-50k), [50k-200k)
            default: "ALTO (>= 200000)",    // Todo valor >= 200000
            output: {
                cantidad: { $sum: 1 },
                proyecciones: {
                    $push: {
                        clienteId: "$_id.clienteId",
                        categoria: "$_id.categoriaNombre",
                        volumen: "$volumenProyectado"
                    }
                }
            }
        }
    }
];

const resultados = db.carritos.aggregate(pipeline).toArray();

if (resultados.length === 0) {
    print("No hay proyecciones de ventas en este momento (ningún carrito activo con ítems).");
} else {
    // Para visualización bonita en mongosh
    printjson(resultados);
}

print("\n--- Fin del Pipeline ---\n");
