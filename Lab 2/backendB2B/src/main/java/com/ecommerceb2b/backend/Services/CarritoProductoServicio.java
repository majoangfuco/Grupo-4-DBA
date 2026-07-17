package com.ecommerceb2b.backend.Services;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Repository.CarritoProductoRepositorio;

@Service
public class CarritoProductoServicio {

	private final CarritoProductoRepositorio carritoProductoRepositorio;

	public CarritoProductoServicio(CarritoProductoRepositorio carritoProductoRepositorio) {
		this.carritoProductoRepositorio = carritoProductoRepositorio;
	}

	// Entradas: idCarrito, idProducto, cantidad
	// Salida: CarritoProductoEntidad (o DTO)
	// Descripcion: agrega un producto al carrito o incrementa su cantidad si ya existe.
	@Transactional
	public CarritoProductoEntidad agregarProducto(Long idCarrito, Long idProducto, int cantidad) {
		if (cantidad <= 0) {
			throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
		}
		CarritoProductoEntidad existente = carritoProductoRepositorio
				.encontrarPorCarritoYProducto(idCarrito, idProducto)
				.orElse(null);

		carritoProductoRepositorio.reservarStock(idProducto, cantidad);
		if (existente != null) {
			Long nuevaCantidad = existente.getUnidad_producto() + cantidad;
			carritoProductoRepositorio.actualizarCantidad(
				existente.getCarrito_Producto_ID(),
				nuevaCantidad
			);
			existente.setUnidad_producto(nuevaCantidad);
			return existente;
		}

		carritoProductoRepositorio.crear(idCarrito, idProducto, (long) cantidad);
		return carritoProductoRepositorio.encontrarPorCarritoYProducto(idCarrito, idProducto)
				.orElseThrow(() -> new IllegalStateException("No se pudo crear el item del carrito"));
	}

	// Entradas: idCarritoProducto
	// Salida: CarritoProductoEntidad (o DTO)
	// Descripcion: obtiene un item del carrito por su id.
	@Transactional(readOnly = true)
	public CarritoProductoEntidad obtenerItemPorId(Long idCarritoProducto) {
		return carritoProductoRepositorio.encontrarPorId(idCarritoProducto)
				.orElseThrow(() -> new NoSuchElementException(
					"Item no encontrado: " + idCarritoProducto
				));
	}

	// Entradas: idCarrito
	// Salida: List<CarritoProductoEntidad> (o DTO)
	// Descripcion: lista los productos dentro de un carrito.
	@Transactional(readOnly = true)
	public List<CarritoProductoEntidad> listarItemsPorCarrito(Long idCarrito) {
		return carritoProductoRepositorio.listarPorCarrito(idCarrito);
	}

	// Entradas: idCarritoProducto, nuevaCantidad
	// Salida: CarritoProductoEntidad (o DTO)
	// Descripcion: actualiza la cantidad de un producto en el carrito.
	@Transactional
	public CarritoProductoEntidad actualizarCantidad(Long idCarritoProducto, int nuevaCantidad) {
		if (nuevaCantidad <= 0) {
			throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
		}

		CarritoProductoEntidad actual = obtenerItemPorId(idCarritoProducto);
		Long cantidadActual = actual.getUnidad_producto();
		int delta = nuevaCantidad - cantidadActual.intValue();

		if (delta > 0) {
			carritoProductoRepositorio.reservarStock(
				actual.getProducto().getProducto_ID(),
				delta
			);
		} else if (delta < 0) {
			carritoProductoRepositorio.liberarStock(
				actual.getProducto().getProducto_ID(),
				Math.abs(delta)
			);
		}

		carritoProductoRepositorio.actualizarCantidad(idCarritoProducto, (long) nuevaCantidad);
		actual.setUnidad_producto((long) nuevaCantidad);
		return actual;
	}

	// Entradas: idCarritoProducto
	// Salida: void
	// Descripcion: elimina un producto del carrito.
	@Transactional
	public void eliminarItem(Long idCarritoProducto) {
		CarritoProductoEntidad actual = obtenerItemPorId(idCarritoProducto);
		carritoProductoRepositorio.liberarStock(
				actual.getProducto().getProducto_ID(),
				actual.getUnidad_producto().intValue()
		);
		carritoProductoRepositorio.eliminarPorId(idCarritoProducto);
	}

	// Entradas: idCarrito
	// Salida: BigDecimal
	// Descripcion: calcula el subtotal del carrito.
	@Transactional(readOnly = true)
	public BigDecimal calcularSubtotal(Long idCarrito) {
		return carritoProductoRepositorio.calcularSubtotal(idCarrito);
	}
}
