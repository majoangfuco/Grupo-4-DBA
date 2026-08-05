package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Services.MongoSesionServicio;
import com.mongodb.client.MongoClient;

import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint de verificación de la infraestructura MongoDB.
 *
 * Permite comprobar de un vistazo que el replica set quedó bien levantado
 * (hay PRIMARY, hay SECONDARY y por lo tanto las transacciones
 * multi-documento y los change streams están habilitados) sin necesidad de
 * abrir mongosh.
 *
 * GET /api/mongo/health
 */
@RestController
@RequestMapping("/api/mongo")
@CrossOrigin(origins = "*")
public class MongoSaludControlador {

    private final MongoClient mongoClient;
    private final MongoSesionServicio mongoSesionServicio;

    public MongoSaludControlador(MongoClient mongoClient,
            MongoSesionServicio mongoSesionServicio) {
        this.mongoClient = mongoClient;
        this.mongoSesionServicio = mongoSesionServicio;
    }

    @GetMapping("/health")
    public ResponseEntity<?> salud() {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        long inicio = System.currentTimeMillis();

        try {
            Document ping = mongoClient.getDatabase("admin")
                    .runCommand(new Document("ping", 1));

            respuesta.put("conectado", ping.getDouble("ok") == 1.0);
            respuesta.put("baseDeDatos", mongoSesionServicio.getBaseDeDatos().getName());
            respuesta.put("latenciaMs", System.currentTimeMillis() - inicio);

            Document estado = mongoClient.getDatabase("admin")
                    .runCommand(new Document("replSetGetStatus", 1));

            respuesta.put("replicaSet", estado.getString("set"));

            List<Map<String, Object>> miembros = new ArrayList<>();
            boolean hayPrimario = false;
            boolean haySecundario = false;

            for (Document miembro : estado.getList("members", Document.class)) {
                String rol = miembro.getString("stateStr");
                hayPrimario |= "PRIMARY".equals(rol);
                haySecundario |= "SECONDARY".equals(rol);

                Map<String, Object> detalle = new LinkedHashMap<>();
                detalle.put("host", miembro.getString("name"));
                detalle.put("estado", rol);
                detalle.put("salud", miembro.getDouble("health"));
                miembros.add(detalle);
            }

            respuesta.put("miembros", miembros);
            // Mongo exige replica set para ambas capacidades: sin PRIMARY no
            // hay transacciones, y sin oplog replicado no hay change streams.
            respuesta.put("transaccionesDisponibles", hayPrimario);
            respuesta.put("changeStreamsDisponibles", hayPrimario && haySecundario);

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            respuesta.put("conectado", false);
            respuesta.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(respuesta);
        }
    }
}
