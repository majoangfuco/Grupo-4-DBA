package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.ZonaProtegidaUpdateDto;
import com.ecommerceb2b.backend.Loader.UnidadVecinalGeoJsonLoader;
import com.ecommerceb2b.backend.Repository.UnidadVecinalRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Administración de unidad_vecinal_entidad. La carga desde el GeoJSON local
 * es un job de ejecución puntual: se dispara a mano una sola vez (o cuando
 * se actualice el archivo fuente) — no corre en el arranque de la app.
 * Protegido con rol ADMIN, ver SecurityConfig ("/api/admin/**").
 *
 * A diferencia de ComunaAdminControlador (que usa JdbcTemplate crudo, una
 * excepción puntual del proyecto), este controlador usa exclusivamente
 * UnidadVecinalRepositorio — sin SQL fuera de la capa de repositorio.
 */
@RestController
@RequestMapping("/api/admin/unidades-vecinales")
@CrossOrigin(origins = "*")
public class UnidadVecinalAdminControlador {

    private final UnidadVecinalGeoJsonLoader loader;
    private final UnidadVecinalRepositorio repositorio;

    public UnidadVecinalAdminControlador(UnidadVecinalGeoJsonLoader loader, UnidadVecinalRepositorio repositorio) {
        this.loader = loader;
        this.repositorio = repositorio;
    }

    // POST /api/admin/unidades-vecinales/cargar
    // Lee resources/data/unidades_vecinales_rm.geojson y hace UPSERT en
    // unidad_vecinal_entidad (idempotente, se puede volver a ejecutar sin duplicar
    // ni resetear es_zona_protegida ya marcada por el admin).
    @PostMapping("/cargar")
    public ResponseEntity<UnidadVecinalGeoJsonLoader.ResultadoCarga> cargar() {
        UnidadVecinalGeoJsonLoader.ResultadoCarga resultado = loader.cargar();
        return ResponseEntity.ok(resultado);
    }

    // GET /api/admin/unidades-vecinales?comunaId={id}
    // FeatureCollection GeoJSON de las UVs de una comuna, para pintar en un
    // mapa Leaflet donde el admin hace click para activar/desactivar
    // es_zona_protegida sobre los polígonos ya existentes.
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listarPorComuna(@RequestParam Long comunaId) {
        return ResponseEntity.ok(repositorio.listarPorComunaGeoJson(comunaId));
    }

    // GET /api/admin/unidades-vecinales/protegidas
    // FeatureCollection GeoJSON de TODAS las UVs con es_zona_protegida=true,
    // sin filtrar por comuna — toggle independiente en el mapa.
    @GetMapping(value = "/protegidas", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listarProtegidas() {
        return ResponseEntity.ok(repositorio.listarProtegidasGeoJson());
    }

    // GET /api/admin/unidades-vecinales/conteo-por-comuna
    // [{ comunaId, total, protegidas }, ...] — sin geometría, para poblar de
    // una vez el contador "X de Y protegidas" por comuna en el combobox de
    // Gestión de Zonas Protegidas (filtrado 100% en memoria tras esta única carga).
    @GetMapping(value = "/conteo-por-comuna", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> conteoPorComuna() {
        return ResponseEntity.ok(repositorio.conteoPorComuna());
    }

    // PATCH /api/admin/unidades-vecinales/{id}/zona-protegida
    // body: { "esZonaProtegida": true/false } — toggle sin dibujar nada.
    @PatchMapping("/{id}/zona-protegida")
    public ResponseEntity<?> actualizarZonaProtegida(@PathVariable Long id,
                                                       @RequestBody ZonaProtegidaUpdateDto dto) {
        if (dto.getEsZonaProtegida() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "esZonaProtegida es requerido"));
        }
        int filasActualizadas = repositorio.actualizarZonaProtegida(id, dto.getEsZonaProtegida());
        if (filasActualizadas == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No existe unidad vecinal con id " + id));
        }
        return ResponseEntity.ok(Map.of("id", id, "esZonaProtegida", dto.getEsZonaProtegida()));
    }
}
