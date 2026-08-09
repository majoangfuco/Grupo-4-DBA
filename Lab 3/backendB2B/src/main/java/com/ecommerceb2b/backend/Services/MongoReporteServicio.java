package com.ecommerceb2b.backend.Services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BucketOptions;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Accumulators;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class MongoReporteServicio {

    private final MongoSesionServicio mongoSesion;

    public MongoReporteServicio(MongoSesionServicio mongoSesion) {
        this.mongoSesion = mongoSesion;
    }

    /**
     * Tarea 4: Aggregation Pipeline
     * Volumen de ventas proyectado por cliente y categoría de producto 
     * con $group, $bucket, $sort, expuesto por endpoint.
     */
    public List<Document> obtenerVolumenVentasProyectado() {
        return mongoSesion.ejecutar((MongoDatabase db) -> {
            MongoCollection<Document> carritosCol = db.getCollection("carritos");

            // 1. $match: carritos activos
            Bson match = Aggregates.match(Filters.eq("estado", "ACTIVO"));

            // 2. $unwind: items
            Bson unwind = Aggregates.unwind("$items");

            // 3. $group: por cliente y categoría
            Document groupId = new Document("clienteId", "$clienteId")
                                   .append("categoriaNombre", "$items.categoriaNombre");
            Bson group = Aggregates.group(
                    groupId,
                    Accumulators.sum("volumenProyectado", "$items.subtotal")
            );

            // 4. $sort: mayor a menor volumen
            Bson sort = Aggregates.sort(Sorts.descending("volumenProyectado"));

            // 5. $bucket: agrupar por el volumen
            BucketOptions bucketOptions = new BucketOptions()
                    .defaultBucket("ALTO (>= 200000)")
                    .output(
                            Accumulators.sum("cantidad", 1),
                            Accumulators.push("proyecciones", 
                                new Document("clienteId", "$_id.clienteId")
                                .append("categoria", "$_id.categoriaNombre")
                                .append("volumen", "$volumenProyectado")
                            )
                    );

            Bson bucket = Aggregates.bucket(
                    "$volumenProyectado",
                    Arrays.asList(0, 50000, 200000),
                    bucketOptions
            );

            // Armar el pipeline
            List<Bson> pipeline = Arrays.asList(match, unwind, group, sort, bucket);

            // Ejecutar la agregación
            List<Document> resultados = new ArrayList<>();
            carritosCol.aggregate(pipeline).into(resultados);

            return resultados;
        });
    }
}
