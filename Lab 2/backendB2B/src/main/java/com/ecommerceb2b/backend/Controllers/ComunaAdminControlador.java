package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Loader.ComunaOverpassLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Administración de comuna_entidad. La carga desde Overpass es un job de
 * ejecución única: se dispara a mano una sola vez (no corre en el arranque
 * de la app). Protegido con rol ADMIN, ver SecurityConfig ("/api/admin/**").
 */
@RestController
@RequestMapping("/api/admin/comunas")
@CrossOrigin(origins = "*")
public class ComunaAdminControlador {

    private final ComunaOverpassLoader loader;
    private final JdbcTemplate jdbc;

    public ComunaAdminControlador(ComunaOverpassLoader loader, JdbcTemplate jdbc) {
        this.loader = loader;
        this.jdbc = jdbc;
    }

    // POST /api/admin/comunas/cargar
    // Descarga las 52 comunas de la RM desde Overpass y las inserta/actualiza
    // en comuna_entidad (UPSERT por nombre, se puede volver a ejecutar sin duplicar).
    @PostMapping("/cargar")
    public ResponseEntity<ComunaOverpassLoader.ResultadoCarga> cargar() {
        ComunaOverpassLoader.ResultadoCarga resultado = loader.cargarComunas();
        return ResponseEntity.ok(resultado);
    }

    // GET /api/admin/comunas -> verificación rápida de lo que quedó cargado
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar() {
        String sql = """
                SELECT id, nombre, distrito_postal, ST_IsValid(geom) AS geom_valida
                FROM comuna_entidad
                ORDER BY distrito_postal, nombre
                """;
        return ResponseEntity.ok(jdbc.queryForList(sql));
    }

    // GET /api/admin/comunas/geojson -> visualizar rápido los polígonos cargados
    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> geojson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(geom)::json,
                            'properties', json_build_object('nombre', nombre, 'distrito_postal', distrito_postal)
                        )
                    ), '[]')
                )::text
                FROM comuna_entidad
                """;
        return ResponseEntity.ok(jdbc.queryForObject(sql, String.class));
    }
}
