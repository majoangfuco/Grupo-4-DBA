package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.VentasPorComunaEntidad;
import com.ecommerceb2b.backend.Repository.VentasPorComunaRepositorio;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Nota: las rutas bajo /api/reportes/** ya están restringidas a rol ADMIN
// en SecurityConfig, así que este controller hereda esa protección sin
// necesidad de tocar la configuración de seguridad.
@RestController
@RequestMapping("/api/reportes/ventas-por-comuna")
@CrossOrigin(origins = "*")
public class ReporteVentasPorComunaControlador {

    private final VentasPorComunaRepositorio repositorio;

    public ReporteVentasPorComunaControlador(VentasPorComunaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    // GET /api/reportes/ventas-por-comuna
    @GetMapping
    public ResponseEntity<List<VentasPorComunaEntidad>> listar() {
        return ResponseEntity.ok(repositorio.findAll());
    }

    // GET /api/reportes/ventas-por-comuna/geojson (para el mapa de análisis de mercado)
    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listarGeoJson() {
        return ResponseEntity.ok(repositorio.findAllAsGeoJson());
    }

    // POST /api/reportes/ventas-por-comuna/refrescar
    // La vista materializada no se actualiza sola: hay que refrescarla
    // manualmente (o vía un job programado) después de nuevas órdenes.
    @PostMapping("/refrescar")
    public ResponseEntity<Map<String, String>> refrescar() {
        repositorio.refrescar();
        return ResponseEntity.ok(Map.of("mensaje", "Vista de ventas por comuna refrescada correctamente"));
    }
}
