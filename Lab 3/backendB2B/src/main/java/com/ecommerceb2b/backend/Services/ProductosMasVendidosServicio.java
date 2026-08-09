package com.ecommerceb2b.backend.Services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Vista materializada de "productos más vendidos" (Lab 3, punto 6).
 *
 * <p>Encapsula el pipeline de agregación con {@code $merge} que deriva la
 * colección {@code productos_mas_vendidos} a partir de las órdenes en
 * estado {@code CONFIRMADA}. Es el mismo pipeline que define
 * {@code mongo/change-streams-merge.js} (ese script lo corre una vez para
 * el backfill inicial y crea la colección + validador + índices); acá vive
 * la versión Java que ejecuta en caliente el
 * {@link com.ecommerceb2b.backend.Workers.ProductosMasVendidosWorker} ante
 * cada evento del change stream.</p>
 *
 * <p><b>Por qué es idempotente:</b> el modo incremental
 * ({@link #refrescarProductos(Collection)}) no le suma el delta de la
 * orden recién confirmada al acumulado previo — recalcula esos productos
 * SUMANDO DE CERO sobre todas sus órdenes confirmadas y pisa el documento
 * con {@code whenMatched: "replace"}. Eso importa porque un change stream
 * garantiza entrega <i>at-least-once</i>: al reanudar desde un resume
 * token, o si el worker muere entre procesar el evento y guardar el
 * checkpoint, el mismo evento se reprocesa. Con un {@code $inc} eso
 * contaría la venta dos veces; recalculando, el resultado es idéntico.</p>
 *
 * <p>No usa transacción: {@code $merge} no puede correr dentro de una
 * transacción multi-documento, y no hace falta — cada ejecución deja la
 * colección en un estado consistente por sí sola.</p>
 */
@Service
public class ProductosMasVendidosServicio {

    public static final String COL_ORDENES = "ordenes";
    public static final String COL_DESTINO = "productos_mas_vendidos";

    /** Estado de orden que cuenta como venta para el ranking. */
    public static final String ESTADO_CONFIRMADA = "CONFIRMADA";

    private final MongoSesionServicio mongoSesion;

    public ProductosMasVendidosServicio(MongoSesionServicio mongoSesion) {
        this.mongoSesion = mongoSesion;
    }

    /**
     * Recalcula el ranking COMPLETO (backfill).
     *
     * Lo usa el endpoint administrativo de recálculo y el worker cuando el
     * change stream perdió su punto de reanudación (oplog rotado) y no
     * puede saber qué se perdió mientras estuvo caído.
     *
     * @return cantidad de productos que quedaron en la vista materializada.
     */
    public long refrescarTodo() {
        return ejecutarMerge(null);
    }

    /**
     * Recalcula SOLO los productos indicados.
     *
     * Es la ruta caliente del change stream: una orden confirmada toca un
     * puñado de productos, así que no tiene sentido reagregar el catálogo
     * entero en cada evento.
     *
     * @param productoIds ids de producto afectados por la orden confirmada.
     * @return cantidad de productos recalculados.
     */
    public long refrescarProductos(Collection<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return 0L;
        }
        return ejecutarMerge(new ArrayList<>(productoIds));
    }

    private long ejecutarMerge(List<Long> productoIds) {
        return mongoSesion.ejecutar((MongoDatabase db) -> {
            // aggregate() con $merge es "lazy": el pipeline no se ejecuta
            // hasta que se consume el cursor. Como $merge no emite
            // documentos, toArray()/into() devuelve vacío pero es lo que
            // dispara la escritura — por eso el first() de más abajo no se
            // puede omitir.
            db.getCollection(COL_ORDENES)
                    .aggregate(construirPipeline(productoIds))
                    .first();

            MongoCollection<Document> destino = db.getCollection(COL_DESTINO);
            return productoIds == null
                    ? destino.countDocuments()
                    : (long) productoIds.size();
        });
    }

    /**
     * Pipeline materializado. Espejo exacto de
     * {@code pipelineProductosMasVendidos()} en
     * {@code mongo/change-streams-merge.js}: si se toca uno, hay que tocar
     * el otro (y el validador $jsonSchema de la colección destino, que
     * describe el shape que emite el $project).
     *
     * @param productoIds {@code null} para recalcular todo el ranking.
     */
    private List<Document> construirPipeline(List<Long> productoIds) {
        List<Document> pipeline = new ArrayList<>();

        // El checkout crea las órdenes en PENDIENTE; solo la confirmación
        // las convierte en venta (enunciado: "cada vez que se confirma una
        // nueva orden").
        pipeline.add(new Document("$match", new Document("estado", ESTADO_CONFIRMADA)));
        pipeline.add(new Document("$unwind", "$items"));

        if (productoIds != null) {
            pipeline.add(new Document("$match",
                    new Document("items.productoId", new Document("$in", productoIds))));
        }

        // Ordenar por fecha antes de agrupar hace que el $last de
        // nombreProducto sea el nombre de la venta más reciente y no uno
        // arbitrario: items[] guarda un snapshot del nombre al momento de
        // comprar, así que órdenes viejas pueden traer otro texto.
        pipeline.add(new Document("$sort", new Document("fechaOrden", 1)));

        pipeline.add(new Document("$group", new Document()
                .append("_id", "$items.productoId")
                .append("nombreProducto", new Document("$last", "$items.nombreProducto"))
                .append("unidadesVendidas", new Document("$sum", "$items.cantidad"))
                .append("montoTotalVendido", new Document("$sum", "$items.subtotal"))
                .append("ordenesConfirmadas", new Document("$sum", 1))
                .append("ultimaVentaEn", new Document("$max",
                        new Document("$ifNull", List.of("$fechaConfirmacion", "$fechaOrden"))))));

        pipeline.add(new Document("$sort", new Document("unidadesVendidas", -1)));

        pipeline.add(new Document("$project", new Document()
                .append("_id", 1)
                .append("productoId", "$_id")
                .append("nombreProducto", 1)
                .append("unidadesVendidas", 1)
                .append("montoTotalVendido", 1)
                .append("ordenesConfirmadas", 1)
                .append("ultimaVentaEn", 1)
                .append("actualizadoEn", "$$NOW")));

        pipeline.add(new Document("$merge", new Document()
                .append("into", COL_DESTINO)
                .append("on", "_id")
                // "replace" y no "merge": el documento recalculado es el
                // estado completo del producto, no un parche parcial.
                .append("whenMatched", "replace")
                .append("whenNotMatched", "insert")));

        return pipeline;
    }

    /**
     * Lectura del ranking ya materializado. No agrega nada: es un find()
     * sobre {@code productos_mas_vendidos} apoyado en el índice
     * {@code ix_masvendidos_unidadesVendidas}, que es justamente la razón
     * de existir de la vista materializada.
     *
     * @param limite cantidad máxima de productos (top N).
     */
    public List<Document> obtenerTop(int limite) {
        int tope = Math.max(1, Math.min(limite, 200));
        return mongoSesion.ejecutar((MongoDatabase db) -> {
            List<Document> resultados = new ArrayList<>();
            db.getCollection(COL_DESTINO)
                    .find()
                    .sort(new Document("unidadesVendidas", -1))
                    .limit(tope)
                    .map(ProductosMasVendidosServicio::normalizarMonto)
                    .into(resultados);
            return resultados;
        });
    }

    /**
     * {@code montoTotalVendido} llega como {@link Decimal128} (el {@code $sum}
     * de {@code items.subtotal}, que el checkout escribe en decimal para no
     * perder centavos). Jackson no conoce ese tipo y lo serializaría como un
     * objeto con {@code high}/{@code low}, así que se convierte a
     * {@link BigDecimal} antes de salir por la API.
     */
    private static Document normalizarMonto(Document doc) {
        if (doc.get("montoTotalVendido") instanceof Decimal128 decimal) {
            doc.put("montoTotalVendido", decimal.bigDecimalValue());
        }
        return doc;
    }
}
