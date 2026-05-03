package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Services.FacturaServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facturas")
@CrossOrigin(origins = "http://localhost:5173")
public class FacturaControlador {

    private final FacturaServicio servicio;

    public FacturaControlador(FacturaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<FacturaEntidad>> obtenerTodas() {
        return ResponseEntity.ok(servicio.obtenerTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaEntidad> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(servicio.obtenerPorId(id));
    }

    // Endpoint 10 del enunciado
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<FacturaEntidad>> obtenerPorUsuario(
            @PathVariable Long usuarioId) {
        return ResponseEntity.ok(servicio.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/orden/{ordenId}")
    public ResponseEntity<FacturaEntidad> obtenerPorOrden(@PathVariable Long ordenId) {
        return ResponseEntity.ok(servicio.obtenerPorOrden(ordenId));
    }
}