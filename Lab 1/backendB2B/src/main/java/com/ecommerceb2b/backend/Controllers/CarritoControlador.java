package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.CheckoutPedidoDto;
import com.ecommerceb2b.backend.Services.CarritoServicio;
import com.ecommerceb2b.backend.Services.CheckoutServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carritos")
@CrossOrigin(origins = "*")
public class CarritoControlador {

	private final CarritoServicio carritoServicio;
	private final CheckoutServicio checkoutServicio;

	public CarritoControlador(CarritoServicio carritoServicio, CheckoutServicio checkoutServicio) {
		this.carritoServicio = carritoServicio;
		this.checkoutServicio = checkoutServicio;
	}

	// GET /api/carritos/cliente/{idCliente}/activo
	@GetMapping("/cliente/{idCliente}/activo")
	public ResponseEntity<?> obtenerOCrear(@PathVariable Long idCliente) {
		try {
			CarritoEntidad carrito = carritoServicio.obtenerOCrearCarrito(idCliente);
			return ResponseEntity.ok(carrito);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Error al obtener carrito");
		}
	}

	// GET /api/carritos/{id}
	@GetMapping("/{id}")
	public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(carritoServicio.obtenerCarritoPorId(id));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// GET /api/carritos/cliente/{idCliente}
	@GetMapping("/cliente/{idCliente}")
	public ResponseEntity<?> listarPorCliente(@PathVariable Long idCliente) {
		try {
			List<CarritoEntidad> carritos = carritoServicio.listarCarritosPorCliente(idCliente);
			return ResponseEntity.ok(carritos);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	// POST /api/carritos/{id}/vaciar
	@PostMapping("/{id}/vaciar")
	public ResponseEntity<?> vaciar(@PathVariable Long id) {
		try {
			carritoServicio.vaciarCarrito(id);
			return ResponseEntity.ok("Carrito vaciado correctamente");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// PATCH /api/carritos/{id}/cerrar
	@PatchMapping("/{id}/cerrar")
	public ResponseEntity<?> cerrar(@PathVariable Long id) {
		try {
			carritoServicio.cerrarCarrito(id);
			return ResponseEntity.ok("Carrito cerrado correctamente");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// PATCH /api/carritos/{id}/pagar
	@PatchMapping("/{id}/pagar")
	public ResponseEntity<?> pagar(@PathVariable Long id) {
		try {
			carritoServicio.pagarCarrito(id);
			return ResponseEntity.ok("Carrito pagado correctamente");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// POST /api/carritos/{id}/checkout
	@PostMapping("/{id}/checkout")
	public ResponseEntity<?> checkout(@PathVariable Long id, @RequestBody CheckoutPedidoDto pedido) {
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(checkoutServicio.procesarCheckout(id, pedido));
		} catch (IllegalArgumentException | IllegalStateException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Error al procesar el checkout");
		}
	}
}
