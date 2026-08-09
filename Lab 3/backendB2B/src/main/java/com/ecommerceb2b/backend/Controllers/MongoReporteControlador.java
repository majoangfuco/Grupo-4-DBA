package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Services.MongoReporteServicio;
import com.ecommerceb2b.backend.Services.ProductosMasVendidosServicio;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes/mongo")
@CrossOrigin(origins = "*")
public class MongoReporteControlador {

    private final MongoReporteServicio mongoReporteServicio;
    private final ProductosMasVendidosServicio productosMasVendidosServicio;

    public MongoReporteControlador(MongoReporteServicio mongoReporteServicio,
            ProductosMasVendidosServicio productosMasVendidosServicio) {
        this.mongoReporteServicio = mongoReporteServicio;
        this.productosMasVendidosServicio = productosMasVendidosServicio;
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

    /**
     * Tarea 6: GET /api/reportes/mongo/productos-mas-vendidos?limite=10
     *
     * Lee la vista materializada {@code productos_mas_vendidos}. No agrega
     * nada en tiempo de request: es un find() ordenado por el índice
     * {@code ix_masvendidos_unidadesVendidas}. El contenido lo mantiene al
     * día el worker de change streams, no este endpoint.
     */
    @GetMapping("/productos-mas-vendidos")
    public ResponseEntity<?> obtenerProductosMasVendidos(
            @RequestParam(defaultValue = "10") int limite) {
        try {
            return ResponseEntity.ok(productosMasVendidosServicio.obtenerTop(limite));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al leer productos_mas_vendidos: " + e.getMessage()));
        }
    }

    /**
     * Tarea 6: POST /api/reportes/mongo/productos-mas-vendidos/recalcular
     *
     * Escotilla de emergencia: reconstruye el ranking completo con el mismo
     * pipeline $merge que usa el worker. Sirve si el worker estuvo caído más
     * tiempo que la ventana del oplog, o para reparar la vista a mano
     * durante la demo. En operación normal no hace falta llamarlo: para eso
     * está el change stream.
     */
    @PostMapping("/productos-mas-vendidos/recalcular")
    public ResponseEntity<?> recalcularProductosMasVendidos() {
        try {
            long productos = productosMasVendidosServicio.refrescarTodo();
            return ResponseEntity.ok(Map.of(
                    "productosEnRanking", productos,
                    "mensaje", "Vista materializada productos_mas_vendidos reconstruida por completo."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al recalcular productos_mas_vendidos: " + e.getMessage()));
        }
    }
}
