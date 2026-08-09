package com.ecommerceb2b.backend.Workers;

import com.ecommerceb2b.backend.Services.MongoSesionServicio;
import com.ecommerceb2b.backend.Services.ProductosMasVendidosServicio;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoInterruptedException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;

import jakarta.annotation.PreDestroy;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Worker de Change Streams (Lab 3, punto 6).
 *
 * <p>Escucha la colección {@code ordenes} y, cada vez que una orden queda
 * en estado {@code CONFIRMADA}, dispara el pipeline {@code $merge} que
 * refresca la vista materializada {@code productos_mas_vendidos}
 * ({@link ProductosMasVendidosServicio}).</p>
 *
 * <p><b>Corre en un proceso aparte del server HTTP.</b> Está anotado con
 * {@code @Profile("worker")}, y ese perfil levanta la aplicación sin
 * Tomcat ({@code spring.main.web-application-type=none} en
 * {@code application-worker.properties}). En docker-compose es el servicio
 * {@code worker}, que reusa la misma imagen que {@code backend} pero con
 * {@code SPRING_PROFILES_ACTIVE=worker}. El backend HTTP nunca instancia
 * esta clase. Las razones de separarlo:</p>
 * <ul>
 *   <li>Un change stream es un cursor <i>tailing</i> que vive para siempre:
 *       no encaja en el ciclo request/response de un controlador.</li>
 *   <li>El listener debe ser único. Si viviera dentro del backend, escalar
 *       el backend a N réplicas abriría N listeners procesando los mismos
 *       eventos.</li>
 *   <li>Aísla fallos: si el worker se cae por un problema del oplog, la
 *       API sigue atendiendo compras.</li>
 * </ul>
 *
 * <p><b>Reanudación.</b> Tras procesar cada evento se persiste su resume
 * token en {@code change_stream_checkpoints}. Al arrancar, el stream se
 * abre con {@code resumeAfter(token)}, de modo que las órdenes confirmadas
 * mientras el worker estuvo caído se procesan igual. Si el oplog ya rotó y
 * el token dejó de ser válido (error {@code ChangeStreamHistoryLost}), no
 * hay forma de saber qué se perdió: se recalcula el ranking completo y se
 * abre un stream nuevo.</p>
 *
 * <p>El reprocesamiento es inofensivo porque el {@code $merge} recalcula
 * cada producto desde cero en vez de acumular deltas — ver Javadoc de
 * {@link ProductosMasVendidosServicio}.</p>
 */
@Component
@Profile("worker")
public class ProductosMasVendidosWorker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductosMasVendidosWorker.class);

    private static final String COL_CHECKPOINTS = "change_stream_checkpoints";
    /** Clave del checkpoint: un documento por listener. */
    private static final String ID_CHECKPOINT = "productos_mas_vendidos";

    /** Código de error de MongoDB cuando el resume token ya no está en el oplog. */
    private static final int CHANGE_STREAM_HISTORY_LOST = 286;

    /** Cuánto espera {@code tryNext()} por eventos antes de devolver null. */
    private static final long ESPERA_EVENTOS_SEGUNDOS = 2L;

    /** Backoff tras un fallo del stream, para no reintentar en bucle cerrado. */
    private static final long REINTENTO_SEGUNDOS = 5L;

    private final MongoSesionServicio mongoSesion;
    private final ProductosMasVendidosServicio masVendidos;

    private volatile boolean activo = true;
    private Thread hilo;
    private long eventosProcesados = 0L;

    public ProductosMasVendidosWorker(MongoSesionServicio mongoSesion,
            ProductosMasVendidosServicio masVendidos) {
        this.mongoSesion = mongoSesion;
        this.masVendidos = masVendidos;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Hilo NO daemon a propósito: es lo único que mantiene viva la JVM
        // en el perfil worker, donde no hay Tomcat que la sostenga.
        hilo = new Thread(this::bucle, "change-stream-productos-mas-vendidos");
        hilo.setDaemon(false);
        hilo.start();
    }

    /** Bucle externo: mantiene el listener vivo ante caídas del stream. */
    private void bucle() {
        log.info("Worker de change streams iniciado (ordenes CONFIRMADA -> {})",
                ProductosMasVendidosServicio.COL_DESTINO);

        while (activo) {
            try {
                escuchar();
            } catch (MongoInterruptedException e) {
                // Apagado normal: el driver aborta la espera del cursor.
                break;
            } catch (MongoCommandException e) {
                if (e.getErrorCode() == CHANGE_STREAM_HISTORY_LOST) {
                    log.warn("El resume token quedó fuera del oplog. Se recalcula el ranking "
                            + "completo y se abre un stream nuevo desde ahora.", e);
                    borrarCheckpoint();
                    continue;
                }
                log.error("Error de MongoDB en el change stream; reintentando en {}s",
                        REINTENTO_SEGUNDOS, e);
                dormir(REINTENTO_SEGUNDOS);
            } catch (RuntimeException e) {
                log.error("Fallo inesperado en el change stream; reintentando en {}s",
                        REINTENTO_SEGUNDOS, e);
                dormir(REINTENTO_SEGUNDOS);
            }
        }

        log.info("Worker de change streams detenido tras procesar {} evento(s)", eventosProcesados);
    }

    /** Bucle interno: abre el cursor y consume eventos hasta que falle o se apague. */
    private void escuchar() {
        MongoDatabase db = mongoSesion.getBaseDeDatos();
        BsonDocument resumeToken = leerCheckpoint();

        ChangeStreamIterable<Document> stream = db
                .getCollection(ProductosMasVendidosServicio.COL_ORDENES)
                .watch(filtroOrdenesConfirmadas())
                // Un update trae solo el delta; con UPDATE_LOOKUP el servidor
                // adjunta el documento completo, que es de donde se sacan los
                // items[].productoId a recalcular (y contra el que se aplica
                // el $match de estado del pipeline de arriba).
                .fullDocument(FullDocument.UPDATE_LOOKUP)
                .maxAwaitTime(ESPERA_EVENTOS_SEGUNDOS, TimeUnit.SECONDS);

        if (resumeToken != null) {
            stream = stream.resumeAfter(resumeToken);
            log.info("Reanudando el change stream desde el último checkpoint guardado");
        }

        try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = stream.cursor()) {
            if (resumeToken == null) {
                // Primer arranque (o historia perdida): el stream solo ve lo
                // que pase de aquí en adelante, así que hay que reconstruir
                // el ranking con las órdenes ya confirmadas. Se hace DESPUÉS
                // de abrir el cursor para no dejar un hueco entre el backfill
                // y el inicio de la escucha; si algún evento cae en medio, se
                // procesa dos veces y no pasa nada (el $merge es idempotente).
                long productos = masVendidos.refrescarTodo();
                log.info("Backfill inicial completado: {} producto(s) en la vista materializada",
                        productos);
            }

            while (activo) {
                ChangeStreamDocument<Document> evento = cursor.tryNext();
                if (evento == null) {
                    continue; // ventana de espera vencida sin eventos
                }
                procesar(evento);
                guardarCheckpoint(evento.getResumeToken());
            }
        }
    }

    /**
     * Filtro aplicado en el SERVIDOR, no en el cliente: el worker solo
     * recibe eventos de órdenes que quedaron CONFIRMADAS, en vez de todo el
     * tráfico de la colección.
     *
     * <p>Los tres tipos de operación cubren las formas en que una orden
     * puede llegar a ese estado: {@code update} es el caso normal
     * (PENDIENTE -> CONFIRMADA), {@code replace} si alguien reescribe el
     * documento entero, e {@code insert} por si el día de mañana alguna ruta
     * crea la orden ya confirmada. {@code delete} no aplica: no trae
     * fullDocument y una orden borrada no es una venta nueva.</p>
     */
    private List<Document> filtroOrdenesConfirmadas() {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document()
                .append("operationType", new Document("$in", List.of("insert", "update", "replace")))
                .append("fullDocument.estado", ProductosMasVendidosServicio.ESTADO_CONFIRMADA)));
        return pipeline;
    }

    private void procesar(ChangeStreamDocument<Document> evento) {
        Document orden = evento.getFullDocument();
        if (orden == null) {
            // La orden se borró entre el evento y el lookup. No hay nada que
            // recalcular con este evento; el checkpoint igual avanza.
            return;
        }

        Set<Long> productoIds = extraerProductoIds(orden);
        if (productoIds.isEmpty()) {
            log.warn("La orden {} quedó CONFIRMADA pero no tiene items con productoId; se ignora",
                    orden.get("_id"));
            return;
        }

        masVendidos.refrescarProductos(productoIds);
        eventosProcesados++;

        log.info("Orden {} ({}) confirmada -> {} producto(s) recalculados en {}",
                orden.get("numeroOrden"), orden.get("_id"), productoIds.size(),
                ProductosMasVendidosServicio.COL_DESTINO);
    }

    /** LinkedHashSet: deduplica si un producto aparece en dos ítems y conserva el orden para los logs. */
    private Set<Long> extraerProductoIds(Document orden) {
        Set<Long> ids = new LinkedHashSet<>();
        List<Document> items = orden.getList("items", Document.class);
        if (items == null) {
            return ids;
        }
        for (Document item : items) {
            if (item.get("productoId") instanceof Number numero) {
                ids.add(numero.longValue());
            }
        }
        return ids;
    }

    // ─── Checkpoints ────────────────────────────────────────────────────

    private MongoCollection<BsonDocument> checkpoints() {
        return mongoSesion.getBaseDeDatos().getCollection(COL_CHECKPOINTS, BsonDocument.class);
    }

    private BsonDocument leerCheckpoint() {
        BsonDocument doc = checkpoints()
                .find(new BsonDocument("_id", new BsonString(ID_CHECKPOINT)))
                .first();
        return doc != null && doc.containsKey("resumeToken")
                ? doc.getDocument("resumeToken")
                : null;
    }

    /**
     * Se guarda DESPUÉS de aplicar el $merge, nunca antes: si el worker
     * muere en medio, el evento se reprocesa (at-least-once) en vez de
     * perderse. Reprocesar es seguro; perder una venta del ranking, no.
     */
    private void guardarCheckpoint(BsonDocument resumeToken) {
        if (resumeToken == null) {
            return;
        }
        checkpoints().replaceOne(
                new BsonDocument("_id", new BsonString(ID_CHECKPOINT)),
                new BsonDocument("_id", new BsonString(ID_CHECKPOINT))
                        .append("resumeToken", resumeToken)
                        .append("actualizadoEn", new BsonDateTime(System.currentTimeMillis()))
                        .append("eventosProcesados", new BsonInt64(eventosProcesados)),
                new ReplaceOptions().upsert(true));
    }

    private void borrarCheckpoint() {
        checkpoints().deleteOne(new BsonDocument("_id", new BsonString(ID_CHECKPOINT)));
    }

    // ─── Apagado ────────────────────────────────────────────────────────

    private void dormir(long segundos) {
        try {
            TimeUnit.SECONDS.sleep(segundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            activo = false;
        }
    }

    @PreDestroy
    public void detener() {
        activo = false;
        if (hilo != null) {
            // No se interrumpe el hilo: tryNext() devuelve solo dentro de
            // ESPERA_EVENTOS_SEGUNDOS y ahí el bucle ve el flag. Interrumpir
            // podría cortar un $merge a mitad de camino (el driver traduce la
            // interrupción a MongoInterruptedException). El join tolera además
            // el backoff de reintento, que es la espera más larga posible.
            try {
                hilo.join(TimeUnit.SECONDS.toMillis(REINTENTO_SEGUNDOS + ESPERA_EVENTOS_SEGUNDOS * 2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
