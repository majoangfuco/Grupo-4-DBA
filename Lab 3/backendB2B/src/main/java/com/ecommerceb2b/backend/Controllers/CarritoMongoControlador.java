package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.CarritoMongoEntidad;
import com.ecommerceb2b.backend.Services.CarritoMongoServicio;
import com.ecommerceb2b.backend.Exceptions.CarritoMongoValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador para la gestión del carrito en MongoDB.
 * Usa /api/mongo/carritos (no /api/carritos): ese prefijo ya
 * lo usa CarritoControlador para el carrito de Postgres.
 * clienteId y productoId son Long, igual que usuario_ID/producto_ID de
 * Postgres.
 */
@RestController
@RequestMapping("/api/mongo/carritos")
@CrossOrigin(origins = "*")
public class CarritoMongoControlador {

    private final CarritoMongoServicio carritoMongoServicio;

    public CarritoMongoControlador(CarritoMongoServicio carritoMongoServicio) {
        this.carritoMongoServicio = carritoMongoServicio;
    }

    public static class AgregarItemRequest {
        public Long productoId;
        public Integer cantidad;
    }

    @PostMapping("/{clienteId}/items")
    public ResponseEntity<?> agregarItem(@PathVariable Long clienteId,
            @RequestBody AgregarItemRequest request) {
        try {
            CarritoMongoEntidad carrito = carritoMongoServicio.agregarItem(
                    clienteId, request.productoId, request.cantidad);
            return ResponseEntity.status(HttpStatus.CREATED).body(carrito);
        } catch (CarritoMongoValidationException e) {.
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{clienteId}")
    public ResponseEntity<?> listar(@PathVariable Long clienteId) {
        try {
            List<CarritoMongoEntidad> carritos = carritoMongoServicio.listar(clienteId);
            return ResponseEntity.ok(carritos);
        } catch (CarritoMongoValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─── Configuración de cantidad mínima B2B por producto ────────

    @PutMapping("/config-productos/{productoId}")
    public ResponseEntity<?> establecerCantidadMinima(@PathVariable Long productoId,
            @RequestBody Integer cantidadMinimaB2B) {
        try {
            carritoMongoServicio.establecerCantidadMinima(productoId, cantidadMinimaB2B);
            return ResponseEntity.ok(Map.of("productoId", productoId, "cantidadMinimaB2B", cantidadMinimaB2B));
        } catch (CarritoMongoValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/config-productos/{productoId}")
    public ResponseEntity<?> obtenerCantidadMinima(@PathVariable Long productoId) {
        Integer cantidadMinima = carritoMongoServicio.obtenerCantidadMinima(productoId);
        return ResponseEntity.ok(Map.of("productoId", productoId, "cantidadMinimaB2B", cantidadMinima));
    }
}