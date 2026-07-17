package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.ReporteVentasEntidad;
import com.ecommerceb2b.backend.Services.ReporteVentasServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin(origins = "*")
public class ReporteVentasControlador {

    private final ReporteVentasServicio reporteVentasServicio;

    public ReporteVentasControlador(ReporteVentasServicio reporteVentasServicio) {
        this.reporteVentasServicio = reporteVentasServicio;
    }

    // ─── Endpoints del Reporte ────────────────────────────────────────────────

    /**
     * GET /api/reportes/ventas
     * Obtiene todos los reportes de ventas mensuales consolidados por categoría
     * Útil para el dashboard general que muestra el histórico completo
     */
    @GetMapping("/ventas")
    public ResponseEntity<?> obtenerTodosLosReportes() {
        try {
            List<ReporteVentasEntidad> reportes = reporteVentasServicio.obtenerTodosLosReportes();
            return ResponseEntity.ok(reportes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al obtener reportes: " + e.getMessage());
        }
    }

    /**
     * GET /api/reportes/ventas/mes?mesAno=2024-03
     * Filtra reportes por un mes y año específico
     * Ejemplo: 2024-03 para marzo de 2024
     */
    @GetMapping("/ventas/mes")
    public ResponseEntity<?> obtenerPorMesAno(@RequestParam String mesAno) {
        try {
            List<ReporteVentasEntidad> reportes = reporteVentasServicio.obtenerPorMesAno(mesAno);
            if (reportes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No hay reportes para el mes: " + mesAno);
            }
            return ResponseEntity.ok(reportes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al obtener reportes: " + e.getMessage());
        }
    }

    /**
     * GET /api/reportes/ventas/categoria?nombre=Equipos%20de%20Computación
     * Filtra reportes por categoría específica
     */
    @GetMapping("/ventas/categoria")
    public ResponseEntity<?> obtenerPorCategoria(@RequestParam String nombre) {
        try {
            List<ReporteVentasEntidad> reportes = reporteVentasServicio.obtenerPorCategoria(nombre);
            if (reportes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No hay reportes para la categoría: " + nombre);
            }
            return ResponseEntity.ok(reportes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al obtener reportes: " + e.getMessage());
        }
    }

    /**
     * GET /api/reportes/ventas/anio?anio=2024
     * Obtiene todos los reportes de un año específico
     */
    @GetMapping("/ventas/anio")
    public ResponseEntity<?> obtenerPorAnio(@RequestParam Integer anio) {
        try {
            List<ReporteVentasEntidad> reportes = reporteVentasServicio.obtenerPorAnio(anio);
            if (reportes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No hay reportes para el año: " + anio);
            }
            return ResponseEntity.ok(reportes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al obtener reportes: " + e.getMessage());
        }
    }

    /**
     * GET /api/reportes/ventas/total
     * Obtiene el total consolidado de todas las ventas
     * Útil para KPIs y métricas generales del dashboard
     */
    @GetMapping("/ventas/total")
    public ResponseEntity<?> obtenerTotalConsolidado() {
        try {
            ReporteVentasEntidad total = reporteVentasServicio.obtenerTotalConsolidado();
            if (total == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No hay datos de ventas disponibles");
            }
            return ResponseEntity.ok(total);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al obtener total: " + e.getMessage());
        }
    }

    /**
     * POST /api/reportes/refrescar
     * Refresca la vista materializada para obtener datos más recientes
     * Endpoint administrativo que puede ser llamado después de cambios significativos
     */
    @PostMapping("/refrescar")
    public ResponseEntity<?> refrescarReportes() {
        try {
            reporteVentasServicio.refrescarReportes();
            return ResponseEntity.ok("Vista materializada refrescada correctamente");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al refrescar vista: " + e.getMessage());
        }
    }
}
