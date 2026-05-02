package com.ecommerceb2b.backend.Services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
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
		return carritoRepositorio.obtenerOCrearCarrito(idCliente);
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
		int filasAfectadas = carritoRepositorio.actualizarEstado(idCarrito, "ABANDONADO");
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo cerrar el carrito: " + idCarrito);
		}
	}

	// Entradas: idCarrito
	// Salida: void
	// Descripcion: paga el carrito y deja el estado en PAGADO.
	@Transactional
	public void pagarCarrito(Long idCarrito) {
		int filasAfectadas = carritoRepositorio.actualizarEstado(idCarrito, "PAGADO");
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo pagar el carrito: " + idCarrito);
		}
	}
}
