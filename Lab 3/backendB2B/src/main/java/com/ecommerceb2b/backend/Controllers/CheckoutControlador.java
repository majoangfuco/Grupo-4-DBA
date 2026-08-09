package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.CheckoutRequestDto;
import com.ecommerceb2b.backend.Entities.CheckoutResultadoDto;
import com.ecommerceb2b.backend.Exceptions.PagoRechazadoException;
import com.ecommerceb2b.backend.Exceptions.StockInsuficienteException;
import com.ecommerceb2b.backend.Services.CheckoutServicio;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Checkout documental (Lab 3): transacción multi-documento sobre MongoDB
 * (colecciones {@code carritos}/{@code productos}/{@code ordenes}/{@code
 * facturas} — ver {@code docs/03-checkout-transaccion.md}).
 *
 * No reemplaza ni sincroniza con {@code POST /api/ordenes/solicitar/{id}}
 * (checkout relacional del Lab 2, que sí asigna almacén vía PostGIS): este
 * endpoint no resuelve almacén ni calcula envío, ver Javadoc de
 * {@link CheckoutServicio}.
 */
@RestController
@RequestMapping("/api/checkout")
@CrossOrigin(origins = "*")
public class CheckoutControlador {

    private final CheckoutServicio checkoutServicio;

    public CheckoutControlador(CheckoutServicio checkoutServicio) {
        this.checkoutServicio = checkoutServicio;
    }

    // POST /api/checkout — rol CLIENTE (ver SecurityConfig)
    @PostMapping
    public ResponseEntity<?> procesarCheckout(@RequestBody CheckoutRequestDto pedido) {
        try {
            CheckoutResultadoDto resultado = checkoutServicio.procesarCheckout(pedido);
            return ResponseEntity.status(HttpStatus.CREATED).body(resultado);

        } catch (StockInsuficienteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));

        } catch (PagoRechazadoException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));

        } catch (IllegalStateException e) {
            // Carrito inexistente / ajeno / no ACTIVO / vacío: 409, no 400 —
            // el request está bien formado, es el estado del recurso el que
            // no permite procesarlo.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al procesar el checkout: " + e.getMessage()));
        }
    }
}
