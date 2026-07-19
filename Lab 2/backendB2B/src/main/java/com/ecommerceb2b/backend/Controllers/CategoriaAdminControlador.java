package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.RestringidaZonaResidencialUpdateDto;
import com.ecommerceb2b.backend.Services.CategoriaServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Administración del flag restringida_zona_residencial de categoria_entidad.
 *
 * Vive en su propia clase bajo /api/admin/categorias (y no dentro de
 * CategoriaControlador, mapeado a /api/categorias) por una razón concreta:
 * Spring concatena el @RequestMapping de la clase con el del método, así
 * que un endpoint /api/admin/categorias/... no se puede declarar dentro de
 * una clase con @RequestMapping("/api/categorias") sin quedar anidado mal
 * (terminaría en /api/categorias/api/admin/categorias/...). Además,
 * /api/categorias/** hoy no tiene una regla PATCH en SecurityConfig (cae en
 * el catch-all .anyRequest().authenticated(), es decir cualquier usuario
 * autenticado, no solo ADMIN) — este endpoint necesita rol ADMIN, y el
 * prefijo /api/admin/** ya lo garantiza sin tocar SecurityConfig, mismo
 * patrón que ComunaAdminControlador y UnidadVecinalAdminControlador.
 */
@RestController
@RequestMapping("/api/admin/categorias")
@CrossOrigin(origins = "*")
public class CategoriaAdminControlador {

    private final CategoriaServicio categoriaServicio;

    public CategoriaAdminControlador(CategoriaServicio categoriaServicio) {
        this.categoriaServicio = categoriaServicio;
    }

    // PATCH /api/admin/categorias/{id}/restringida-zona-residencial
    // body: { "restringidaZonaResidencial": true/false }
    @PatchMapping("/{id}/restringida-zona-residencial")
    public ResponseEntity<?> actualizarRestringidaZonaResidencial(@PathVariable Long id,
                                                                    @RequestBody RestringidaZonaResidencialUpdateDto dto) {
        if (dto.getRestringidaZonaResidencial() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "restringidaZonaResidencial es requerido"));
        }
        try {
            categoriaServicio.actualizarRestringidaZonaResidencial(id, dto.getRestringidaZonaResidencial());
            return ResponseEntity.ok(Map.of("id", id, "restringidaZonaResidencial", dto.getRestringidaZonaResidencial()));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
