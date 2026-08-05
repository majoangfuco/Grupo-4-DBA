package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Services.GeocodificacionServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Geocodificación de direcciones para el buscador del mapa admin — proxy
 * hacia Nominatim (OSM). Protegido con rol ADMIN, ver SecurityConfig
 * ("/api/admin/**").
 */
@RestController
@RequestMapping("/api/admin/geocodificar")
@CrossOrigin(origins = "*")
public class GeocodificacionControlador {

    private static final Logger log = LoggerFactory.getLogger(GeocodificacionControlador.class);

    private final GeocodificacionServicio geocodificacionServicio;

    public GeocodificacionControlador(GeocodificacionServicio geocodificacionServicio) {
        this.geocodificacionServicio = geocodificacionServicio;
    }

    // GET /api/admin/geocodificar?q={texto}
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buscar(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "q es requerido"));
        }

        try {
            List<Map<String, Object>> resultados = geocodificacionServicio.buscar(q);
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            log.error("Error consultando Nominatim para q='{}': {}", q, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "No se pudo consultar el servicio de geocodificación"));
        }
    }
}
