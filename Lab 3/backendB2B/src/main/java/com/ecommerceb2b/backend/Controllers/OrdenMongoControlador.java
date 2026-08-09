package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Services.OrdenMongoServicio;

import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Órdenes documentales (colección {@code ordenes} de MongoDB).
 *
 * <p>Namespace separado de {@link OrdenesControlador} ({@code /api/ordenes},
 * órdenes relacionales del Lab 2) a propósito: son dos sistemas distintos
 * que no se sincronizan.</p>
 *
 * <p>Confirmar una orden acá es lo que dispara el change stream del punto 6
 * (ver {@code com.ecommerceb2b.backend.Workers.ProductosMasVendidosWorker}).
 * Este endpoint NO actualiza el ranking: solo cambia el estado, y el worker
 * reacciona por su cuenta.</p>
 */
@RestController
@RequestMapping("/api/ordenes/mongo")
@CrossOrigin(origins = "*")
public class OrdenMongoControlador {

    private final OrdenMongoServicio ordenMongoServicio;

    public OrdenMongoControlador(OrdenMongoServicio ordenMongoServicio) {
        this.ordenMongoServicio = ordenMongoServicio;
    }

    /**
     * PATCH /api/ordenes/mongo/{ordenId}/confirmar — rol ADMIN.
     *
     * PATCH y no POST por consistencia con {@code /api/ordenes/{id}/aprobar}
     * del flujo relacional: es una modificación parcial del estado de un
     * recurso que ya existe.
     */
    @PatchMapping("/{ordenId}/confirmar")
    public ResponseEntity<?> confirmar(@PathVariable String ordenId) {
        try {
            Document orden = ordenMongoServicio.confirmar(ordenId);
            return ResponseEntity.ok(Map.of(
                    "ordenId", ordenId,
                    "numeroOrden", String.valueOf(orden.get("numeroOrden")),
                    "estado", String.valueOf(orden.get("estado")),
                    "mensaje", "Orden confirmada. El worker de change streams "
                            + "actualizará productos_mas_vendidos de forma asíncrona."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al confirmar la orden: " + e.getMessage()));
        }
    }
}
