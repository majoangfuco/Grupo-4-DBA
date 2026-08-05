package com.ecommerceb2b.backend.Services;

import com.mongodb.MongoException;
import com.mongodb.TransactionOptions;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Capa de sesiones de MongoDB.
 *
 * Centraliza la apertura de {@link ClientSession} y la ejecución de
 * transacciones multi-documento para que ningún servicio tenga que repetir
 * el manejo de commit/abort. El checkout (punto 3) se construye sobre
 * {@link #enTransaccion(BiFunction)}.
 *
 * Uso típico:
 *
 * <pre>
 * Document factura = mongoSesion.enTransaccion((sesion, db) -&gt; {
 *     db.getCollection("productos").updateOne(sesion, filtro, descuentoStock);
 *     db.getCollection("ordenes").insertOne(sesion, orden);
 *     return db.getCollection("facturas").insertOne(sesion, factura);
 * });
 * </pre>
 *
 * Si el lambda lanza una excepción, la transacción se aborta y NADA queda
 * escrito: ese es el rollback exigido cuando el pago es rechazado.
 */
@Service
public class MongoSesionServicio {

    /**
     * Opciones aplicadas a todas las transacciones del sistema.
     * majority + snapshot es la combinación que garantiza que lo que se lee
     * dentro de la transacción no cambie a mitad de camino (evita, por
     * ejemplo, vender dos veces la última unidad de stock).
     */
    private static final TransactionOptions OPCIONES_TRANSACCION = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .maxCommitTime(15L, TimeUnit.SECONDS)
            .build();

    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;

    public MongoSesionServicio(MongoClient mongoClient, MongoDatabase mongoDatabase) {
        this.mongoClient = mongoClient;
        this.mongoDatabase = mongoDatabase;
    }

    /** Handle directo a la base para operaciones sueltas sin transacción. */
    public MongoDatabase getBaseDeDatos() {
        return mongoDatabase;
    }

    /**
     * Ejecuta la operación dentro de una transacción multi-documento.
     *
     * withTransaction() del driver ya reintenta automáticamente los errores
     * transitorios (TransientTransactionError) y los commits inciertos
     * (UnknownTransactionCommitResult), que es justo lo que ocurre durante
     * una elección de nuevo PRIMARY.
     *
     * @throws MongoException si la transacción no pudo confirmarse; en ese
     *                        caso el driver ya ejecutó el abort.
     */
    public <T> T enTransaccion(BiFunction<ClientSession, MongoDatabase, T> operacion) {
        try (ClientSession sesion = mongoClient.startSession()) {
            return sesion.withTransaction(
                    () -> operacion.apply(sesion, mongoDatabase),
                    OPCIONES_TRANSACCION);
        }
    }

    /**
     * Ejecuta la operación dentro de una sesión (sin transacción).
     *
     * Sirve para agrupar varias lecturas con garantía de "causal
     * consistency": lo escrito antes en la misma sesión se ve sí o sí,
     * aunque la lectura caiga en el secundario.
     */
    public <T> T enSesion(BiFunction<ClientSession, MongoDatabase, T> operacion) {
        try (ClientSession sesion = mongoClient.startSession()) {
            return operacion.apply(sesion, mongoDatabase);
        }
    }

    /** Atajo para operaciones simples que solo necesitan la base. */
    public <T> T ejecutar(Function<MongoDatabase, T> operacion) {
        return operacion.apply(mongoDatabase);
    }
}
