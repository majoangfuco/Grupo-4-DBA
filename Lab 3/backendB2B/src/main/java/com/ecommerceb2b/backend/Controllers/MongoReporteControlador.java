package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Services.MongoReporteServicio;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reportes/mongo")
@CrossOrigin(origins = "*")
public class MongoReporteControlador {

    private final MongoReporteServicio mongoReporteServicio;

    public MongoReporteControlador(MongoReporteServicio mongoReporteServicio) {
        this.mongoReporteServicio = mongoReporteServicio;
    }

    /**
     * Tarea 4: GET /api/reportes/mongo/volumen-proyectado
     * Expone el volumen de ventas proyectado utilizando $group, $bucket y $sort
     */
    @GetMapping("/volumen-proyectado")
    public ResponseEntity<?> obtenerVolumenProyectado() {
        try {
            List<Document> reporte = mongoReporteServicio.obtenerVolumenVentasProyectado();
            return ResponseEntity.ok(reporte);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al generar el reporte de volumen proyectado en MongoDB: " + e.getMessage());
        }
    }
}
