package com.ecommerceb2b.backend.Services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.Date;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Ciclo de vida de las órdenes documentales (colección {@code ordenes} de
 * MongoDB, las que crea {@link CheckoutServicio}).
 *
 * <p>Existe por el punto 6 del laboratorio: el enunciado pide actualizar la
 * vista materializada "cada vez que <b>se confirma</b> una nueva orden",
 * pero el checkout las deja en {@code PENDIENTE} — el pago es un mock y la
 * orden todavía no cuenta como venta. Este servicio aporta la transición
 * {@code PENDIENTE -> CONFIRMADA}, que es el evento que dispara el change
 * stream de
 * {@code com.ecommerceb2b.backend.Workers.ProductosMasVendidosWorker}.</p>
 *
 * <p>No toca la vista materializada ni la llama: esa es exactamente la
 * gracia del punto 6. Acá solo se escribe el estado de la orden; el
 * ranking se actualiza reactivamente en el proceso worker, desacoplado de
 * esta request.</p>
 *
 * <p>Ojo: estas órdenes son las de MongoDB, no las de
 * {@code orden_entidad} en PostgreSQL que gestiona {@link OrdenesServicio}
 * (el flujo del Lab 2, con asignación de almacén por PostGIS). Los dos
 * mundos no se sincronizan — ver Javadoc de {@link CheckoutServicio}.</p>
 */
@Service
public class OrdenMongoServicio {

    private static final String COL_ORDENES = "ordenes";

    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_CONFIRMADA = ProductosMasVendidosServicio.ESTADO_CONFIRMADA;

    private final MongoSesionServicio mongoSesion;

    public OrdenMongoServicio(MongoSesionServicio mongoSesion) {
        this.mongoSesion = mongoSesion;
    }

    /**
     * Confirma una orden PENDIENTE.
     *
     * <p>El filtro incluye {@code estado: "PENDIENTE"}, así que la
     * transición es atómica y no re-confirmable: dos requests concurrentes
     * sobre la misma orden producen una sola confirmación (y por lo tanto un
     * solo evento de change stream), porque la segunda ya no matchea.</p>
     *
     * @param ordenIdHex {@code _id} de la orden en hexadecimal (el
     *                   {@code ordenId} que devuelve {@code POST /api/checkout}).
     * @return la orden ya confirmada.
     * @throws IllegalArgumentException si el id no es un ObjectId válido.
     * @throws IllegalStateException    si la orden no existe o no estaba
     *                                  PENDIENTE.
     */
    public Document confirmar(String ordenIdHex) {
        if (ordenIdHex == null || !ObjectId.isValid(ordenIdHex)) {
            throw new IllegalArgumentException("El id de orden '" + ordenIdHex + "' no es válido");
        }
        ObjectId ordenId = new ObjectId(ordenIdHex);

        return mongoSesion.ejecutar((MongoDatabase db) -> {
            MongoCollection<Document> ordenes = db.getCollection(COL_ORDENES);

            Document confirmada = ordenes.findOneAndUpdate(
                    and(eq("_id", ordenId), eq("estado", ESTADO_PENDIENTE)),
                    Updates.combine(
                            Updates.set("estado", ESTADO_CONFIRMADA),
                            // El pipeline de la vista materializada usa este
                            // campo para `ultimaVentaEn` (con fallback a
                            // fechaOrden en las órdenes previas a este flujo).
                            Updates.set("fechaConfirmacion", new Date())),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

            if (confirmada != null) {
                return confirmada;
            }

            // No matcheó: distinguir "no existe" de "ya no estaba PENDIENTE"
            // para poder devolver un mensaje útil.
            Document actual = ordenes.find(eq("_id", ordenId)).first();
            if (actual == null) {
                throw new IllegalStateException("La orden " + ordenIdHex + " no existe");
            }
            throw new IllegalStateException("La orden " + ordenIdHex + " está en estado "
                    + actual.getString("estado") + ", solo se puede confirmar una orden "
                    + ESTADO_PENDIENTE);
        });
    }
}
