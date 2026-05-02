package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import com.ecommerceb2b.backend.Services.InformacionEntregaServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entregas")
@CrossOrigin(origins = "http://localhost:5173")
public class InformacionEntregaControlador {

    private final InformacionEntregaServicio servicio;

    public InformacionEntregaControlador(InformacionEntregaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<InformacionEntregaEntidad>> obtenerTodas() {
        return ResponseEntity.ok(servicio.obtenerTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InformacionEntregaEntidad> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(servicio.obtenerPorId(id));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<InformacionEntregaEntidad>> obtenerPorUsuario(
            @PathVariable Long usuarioId) {
        return ResponseEntity.ok(servicio.obtenerPorUsuario(usuarioId));
    }

    @PostMapping
    public ResponseEntity<String> crear(@RequestBody InformacionEntregaEntidad entrega) {
        return ResponseEntity.ok(servicio.crear(entrega));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> actualizar(@PathVariable Long id,
                                             @RequestBody InformacionEntregaEntidad entrega) {
        return ResponseEntity.ok(servicio.actualizar(id, entrega));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Long id) {
        return ResponseEntity.ok(servicio.eliminar(id));
    }
}