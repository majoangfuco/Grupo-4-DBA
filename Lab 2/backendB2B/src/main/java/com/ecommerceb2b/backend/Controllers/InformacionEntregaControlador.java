package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import com.ecommerceb2b.backend.Services.InformacionEntregaServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entregas")
@CrossOrigin(origins = "*")
public class InformacionEntregaControlador {

    private final InformacionEntregaServicio servicio;

    public InformacionEntregaControlador(
            InformacionEntregaServicio servicio
    ) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<InformacionEntregaEntidad>> obtenerTodas() {
        return ResponseEntity.ok(servicio.obtenerTodas());
    }

    @GetMapping("/comunas")
    public ResponseEntity<List<String>> obtenerComunasDisponibles() {
        return ResponseEntity.ok(
                servicio.obtenerComunasDisponibles()
        );
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<InformacionEntregaEntidad>> obtenerPorUsuario(
            @PathVariable Long usuarioId
    ) {
        return ResponseEntity.ok(
                servicio.obtenerPorUsuario(usuarioId)
        );
    }

    @GetMapping(value = "/geojson", produces = "application/json")
    public ResponseEntity<String> obtenerTodasGeoJson() {
        return ResponseEntity.ok(
                servicio.findAllAsGeoJson()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<InformacionEntregaEntidad> obtenerPorId(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                servicio.obtenerPorId(id)
        );
    }

    @PostMapping
    public ResponseEntity<String> crear(
            @RequestBody InformacionEntregaEntidad entrega
    ) {
        try {
            return ResponseEntity.ok(
                    servicio.crear(entrega)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    e.getMessage()
            );
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> actualizar(
            @PathVariable Long id,
            @RequestBody InformacionEntregaEntidad entrega
    ) {
        try {
            return ResponseEntity.ok(
                    servicio.actualizar(id, entrega)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    e.getMessage()
            );
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminar(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                servicio.eliminar(id)
        );
    }
}
