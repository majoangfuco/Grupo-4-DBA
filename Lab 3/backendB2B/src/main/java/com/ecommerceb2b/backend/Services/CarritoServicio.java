package com.ecommerceb2b.backend.Services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Repository.CarritoRepositorio;

import java.util.List;
import java.util.NoSuchElementException;



@Service
public class CarritoServicio {

    private final CarritoRepositorio carritoRepositorio;
	private final CarritoProductoServicio carritoProductoServicio;

	public CarritoServicio(CarritoRepositorio carritoRepositorio,
						   CarritoProductoServicio carritoProductoServicio) {
        this.carritoRepositorio = carritoRepositorio;
		this.carritoProductoServicio = carritoProductoServicio;
    }


	// Entradas: idCliente
	// Salida: CarritoEntidad 
	// Descripcion: obtiene el carrito activo del cliente; si no existe, lo crea vacio.

	@Transactional
	public com.ecommerceb2b.backend.Entities.CarritoEntidad obtenerOCrearCarrito(Long idCliente) {
		if (idCliente == null || idCliente <= 0) {
			throw new IllegalArgumentException("El cliente es obligatorio");
		}
		List<com.ecommerceb2b.backend.Entities.CarritoEntidad> existentes =
				carritoRepositorio.encontrarActivoOAbandonadoPorUsuario(idCliente);
		if (existentes.isEmpty()) return carritoRepositorio.crearCarrito(idCliente);

		com.ecommerceb2b.backend.Entities.CarritoEntidad carrito = existentes.get(0);
		if ("ABANDONADO".equalsIgnoreCase(carrito.getEstado())) {
			// Reactivar el carrito sin bloquear la operación de alta.
			// La validación dura de stock y mínimo B2B se vuelve a aplicar
			// cuando el cliente agrega o actualiza un ítem.
			carritoRepositorio.reactivarCarrito(carrito.getCarrito_ID());
			carrito.setEstado("ACTIVO");
		} else {
			carritoRepositorio.refrescarActividad(carrito.getCarrito_ID());
		}
		carrito.setUltima_Actualizacion(new java.sql.Timestamp(System.currentTimeMillis()));
		return carrito;
	}

	// Entradas: idCarrito
	// Salida: CarritoEntidad 
	// Descripcion: retorna el carrito con su detalle de productos.
	@Transactional(readOnly = true)
	public com.ecommerceb2b.backend.Entities.CarritoEntidad obtenerCarritoPorId(Long idCarrito) {
		return carritoRepositorio.encontrarPorId(idCarrito)
				.orElseThrow(() -> new NoSuchElementException(
					"Carrito no encontrado: " + idCarrito
				));
	}

	// Entradas: idCliente
	// Salida: List<CarritoEntidad> 
	// Descripcion: lista historica de carritos del cliente (abiertos y cerrados).
	@Transactional(readOnly = true)
	public java.util.List<com.ecommerceb2b.backend.Entities.CarritoEntidad> listarCarritosPorCliente(Long idCliente) {
		if (idCliente == null || idCliente <= 0) {
			throw new IllegalArgumentException("El cliente es obligatorio");
		}
		return carritoRepositorio.listarPorUsuario(idCliente);
	}

	// Entradas: idCarrito
	// Salida: void
	// Descripcion: vacia el carrito eliminando sus productos.
	@Transactional
	public void vaciarCarrito(Long idCarrito) {
		obtenerCarritoPorId(idCarrito);
		List<CarritoProductoEntidad> items = carritoProductoServicio.listarItemsPorCarrito(idCarrito);
		for (CarritoProductoEntidad item : items) {
			carritoProductoServicio.eliminarItem(item.getCarrito_Producto_ID());
		}
	}

	// Entradas: idCarrito
	// Salida: void
	// Descripcion: marca el carrito como cerrado (pre-checkout local).
	@Transactional
	public void cerrarCarrito(Long idCarrito) {
		com.ecommerceb2b.backend.Entities.CarritoEntidad carrito = obtenerCarritoPorId(idCarrito);
		if (!"ACTIVO".equalsIgnoreCase(carrito.getEstado())) {
			throw new IllegalStateException("Solo un carrito ACTIVO puede abandonarse");
		}
		// Se libera antes de activar el TTL; cuando Mongo borre el documento ya
		// no quedará ninguna reserva huérfana en PostgreSQL.
		for (CarritoProductoEntidad item : carritoProductoServicio.listarItemsPorCarrito(idCarrito)) {
			carritoProductoServicio.liberarStockPorAbandono(item);
		}
		int filasAfectadas = carritoRepositorio.actualizarEstado(idCarrito, "ABANDONADO");
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo cerrar el carrito: " + idCarrito);
		}
	}

	// Entradas: idCarrito
	// Salida: void
	// Descripcion: marca el carrito como pagado, lo cual consume el stock reservado.
	@Transactional
	public void ordenarCarrito(Long idCarrito) {
		int filasAfectadas = carritoRepositorio.actualizarEstado(idCarrito, "PAGADO");
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo marcar el carrito como pagado: " + idCarrito);
		}
	}

	// Alias mantenido por compatibilidad con endpoints existentes.
	@Transactional
	public void pagarCarrito(Long idCarrito) {
		ordenarCarrito(idCarrito);
	}
}
