package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.AlmacenEntidad;
import com.ecommerceb2b.backend.Repository.AlmacenRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/almacenes")
@CrossOrigin(origins = "*")
public class AlmacenControlador {

    private final AlmacenRepositorio almacenRepositorio;

    public AlmacenControlador(AlmacenRepositorio almacenRepositorio) {
        this.almacenRepositorio = almacenRepositorio;
    }

    // Listado normal (para tablas/administración)
    @GetMapping
    public ResponseEntity<List<AlmacenEntidad>> listar() {
        return ResponseEntity.ok(almacenRepositorio.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        return almacenRepositorio.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Almacén no encontrado")));
    }

    // Listado en GeoJSON, para pintar los almacenes en un mapa
    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listarGeoJson() {
        return ResponseEntity.ok(almacenRepositorio.findAllAsGeoJson());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody AlmacenEntidad a) {
        Long id = almacenRepositorio.crear(a);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "almacen_id", id,
                "mensaje", "Almacén creado exitosamente"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id, @RequestBody AlmacenEntidad a) {
        a.setAlmacenId(id);
        int filas = almacenRepositorio.actualizar(a);
        if (filas == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Almacén no encontrado"));
        }
        return ResponseEntity.ok(Map.of("mensaje", "Almacén actualizado exitosamente"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        int filas = almacenRepositorio.borrarPorId(id);
        if (filas == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Almacén no encontrado"));
        }
        return ResponseEntity.ok(Map.of("mensaje", "Almacén eliminado exitosamente"));
    }
}
