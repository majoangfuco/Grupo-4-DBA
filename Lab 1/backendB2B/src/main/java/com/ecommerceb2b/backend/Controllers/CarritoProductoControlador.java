package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Services.CarritoProductoServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/carrito-productos")
@CrossOrigin(origins = "*")
public class CarritoProductoControlador {

	private final CarritoProductoServicio carritoProductoServicio;

	public CarritoProductoControlador(CarritoProductoServicio carritoProductoServicio) {
		this.carritoProductoServicio = carritoProductoServicio;
	}

	public static class AgregarProductoRequest {
		public Long carritoId;
		public Long productoId;
		public int cantidad;
	}

	public static class ActualizarCantidadRequest {
		public int cantidad;
	}

	// POST /api/carrito-productos
	@PostMapping
	public ResponseEntity<?> agregar(@RequestBody AgregarProductoRequest request) {
		try {
			CarritoProductoEntidad creado = carritoProductoServicio.agregarProducto(
				request.carritoId,
				request.productoId,
				request.cantidad
			);
			return ResponseEntity.status(HttpStatus.CREATED).body(creado);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Error al agregar producto");
		}
	}

	// GET /api/carrito-productos/{id}
	@GetMapping("/{id}")
	public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(carritoProductoServicio.obtenerItemPorId(id));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// GET /api/carrito-productos/carrito/{carritoId}
	@GetMapping("/carrito/{carritoId}")
	public ResponseEntity<List<CarritoProductoEntidad>> listarPorCarrito(@PathVariable Long carritoId) {
		return ResponseEntity.ok(carritoProductoServicio.listarItemsPorCarrito(carritoId));
	}

	// PATCH /api/carrito-productos/{id}
	@PatchMapping("/{id}")
	public ResponseEntity<?> actualizarCantidad(@PathVariable Long id,
												@RequestBody ActualizarCantidadRequest request) {
		try {
			return ResponseEntity.ok(carritoProductoServicio.actualizarCantidad(id, request.cantidad));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// DELETE /api/carrito-productos/{id}
	@DeleteMapping("/{id}")
	public ResponseEntity<?> eliminar(@PathVariable Long id) {
		try {
			carritoProductoServicio.eliminarItem(id);
			return ResponseEntity.ok("Item eliminado correctamente");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// GET /api/carrito-productos/carrito/{carritoId}/subtotal
	@GetMapping("/carrito/{carritoId}/subtotal")
	public ResponseEntity<BigDecimal> subtotal(@PathVariable Long carritoId) {
		return ResponseEntity.ok(carritoProductoServicio.calcularSubtotal(carritoId));
	}
}
