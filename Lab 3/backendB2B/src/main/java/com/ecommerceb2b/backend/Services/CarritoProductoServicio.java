package com.ecommerceb2b.backend.Services;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Exceptions.CarritoMongoValidationException;
import com.ecommerceb2b.backend.Repository.CarritoProductoRepositorio;
import com.ecommerceb2b.backend.Repository.CarritoRepositorio;

@Service
public class CarritoProductoServicio {

	private final CarritoProductoRepositorio carritoProductoRepositorio;
	private final CarritoRepositorio carritoRepositorio;
	private final CarritoMongoServicio carritoMongoServicio;

	public CarritoProductoServicio(CarritoProductoRepositorio carritoProductoRepositorio,
			CarritoRepositorio carritoRepositorio,
			CarritoMongoServicio carritoMongoServicio) {
		this.carritoProductoRepositorio = carritoProductoRepositorio;
		this.carritoRepositorio = carritoRepositorio;
		this.carritoMongoServicio = carritoMongoServicio;
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

		long cantidadFinal = cantidad;
		long deltaStock = cantidad;
		if (existente != null) {
			cantidadFinal = existente.getUnidad_producto() + cantidad;
		}
		Integer cantidadMinimaB2B = carritoMongoServicio.obtenerCantidadMinima(idProducto);
		if (cantidadMinimaB2B != null && cantidadFinal < cantidadMinimaB2B) {
			throw new IllegalArgumentException(
					"La cantidad minima para este producto es " + cantidadMinimaB2B);
		}

		if (deltaStock > 0) {
			reservarStockValidado(idProducto, (int) deltaStock);
		}

		CarritoProductoEntidad resultado;
		if (existente != null) {
			carritoProductoRepositorio.actualizarCantidad(
				existente.getCarrito_Producto_ID(),
				cantidadFinal,
				cantidadMinimaB2B
			);
			existente.setUnidad_producto(cantidadFinal);
			resultado = existente;
		} else {
			carritoProductoRepositorio.crear(idCarrito, idProducto, (long) cantidad, cantidadMinimaB2B);
			resultado = carritoProductoRepositorio.encontrarPorCarritoYProducto(idCarrito, idProducto)
					.orElseThrow(() -> new IllegalStateException("No se pudo crear el item del carrito"));
		}

		CarritoEntidad carrito = carritoRepositorio.encontrarPorId(idCarrito)
				.orElseThrow(() -> new IllegalStateException("Carrito no encontrado: " + idCarrito));
		Long clienteId = carrito.getUsuario().getUsuario_ID();
		try {
			actualizarMinimoB2B(resultado, idProducto);
			carritoMongoServicio.establecerCantidadItem(clienteId, idProducto, cantidadFinal);
		} catch (CarritoMongoValidationException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
		return resultado;
	}

	// Entradas: idCarritoProducto
	// Salida: CarritoProductoEntidad (o DTO)
	// Descripcion: obtiene un item del carrito por su id.
	@Transactional(readOnly = true)
	public CarritoProductoEntidad obtenerItemPorId(Long idCarritoProducto) {
		CarritoProductoEntidad item = carritoProductoRepositorio.encontrarPorId(idCarritoProducto)
				.orElseThrow(() -> new NoSuchElementException(
					"Item no encontrado: " + idCarritoProducto
				));
		if (item.getProducto() != null && item.getProducto().getProducto_ID() != null) {
			actualizarMinimoB2B(item, item.getProducto().getProducto_ID());
		}
		return item;
	}

	// Entradas: idCarrito
	// Salida: List<CarritoProductoEntidad> (o DTO)
	// Descripcion: lista los productos dentro de un carrito.
	@Transactional(readOnly = true)
	public List<CarritoProductoEntidad> listarItemsPorCarrito(Long idCarrito) {
		List<CarritoProductoEntidad> items = carritoProductoRepositorio.listarPorCarrito(idCarrito);
		for (CarritoProductoEntidad item : items) {
			if (item.getProducto() != null && item.getProducto().getProducto_ID() != null) {
				actualizarMinimoB2B(item, item.getProducto().getProducto_ID());
			}
		}
		return items;
	}

	// Entradas: idCarritoProducto, nuevaCantidad
	// Salida: CarritoProductoEntidad (o DTO)
	// Descripcion: actualiza la cantidad de un producto en el carrito.
	// los botones +/- de "Mi carrito" llaman acá, así que también tienen que pasar
	// por el validador de Mongo.
	@Transactional
	public CarritoProductoEntidad actualizarCantidad(Long idCarritoProducto, int nuevaCantidad) {
		if (nuevaCantidad <= 0) {
			throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
		}

		CarritoProductoEntidad actual = obtenerItemPorId(idCarritoProducto);
		if (!carritoProductoRepositorio.carritoEstaActivo(actual.getCarrito().getCarrito_ID())) {
			throw new IllegalStateException("Solo se pueden modificar ítems de un carrito ACTIVO");
		}
		Long cantidadActual = actual.getUnidad_producto();
		Integer cantidadMinimaB2B = carritoMongoServicio.obtenerCantidadMinima(actual.getProducto().getProducto_ID());
		if (cantidadMinimaB2B != null && nuevaCantidad < cantidadMinimaB2B) {
			throw new IllegalArgumentException(
					"La cantidad minima para este producto es " + cantidadMinimaB2B);
		}
		int delta = nuevaCantidad - cantidadActual.intValue();

		if (delta > 0) {
			reservarStockValidado(
				actual.getProducto().getProducto_ID(),
				delta
			);
		} else if (delta < 0) {
			liberarStockValidado(
				actual.getProducto().getProducto_ID(),
				Math.abs(delta)
			);
		}

		carritoProductoRepositorio.actualizarCantidad(idCarritoProducto, (long) nuevaCantidad, cantidadMinimaB2B);
		actual.setUnidad_producto((long) nuevaCantidad);

		CarritoEntidad carrito = carritoRepositorio.encontrarPorId(actual.getCarrito().getCarrito_ID())
				.orElseThrow(() -> new IllegalStateException(
						"Carrito no encontrado: " + actual.getCarrito().getCarrito_ID()));
		Long clienteId = carrito.getUsuario().getUsuario_ID();
		try {
			carritoMongoServicio.establecerCantidadItem(clienteId, actual.getProducto().getProducto_ID(), (long) nuevaCantidad);
		} catch (CarritoMongoValidationException e) {
			throw new IllegalArgumentException(e.getMessage());
		}

		return actual;
	}

	// Entradas: idCarritoProducto
	// Salida: void
	// Descripcion: elimina un producto del carrito.
	@Transactional
	public void eliminarItem(Long idCarritoProducto) {
		CarritoProductoEntidad actual = obtenerItemPorId(idCarritoProducto);
		if (!carritoProductoRepositorio.carritoEstaActivo(actual.getCarrito().getCarrito_ID())) {
			throw new IllegalStateException("Solo se pueden eliminar ítems de un carrito ACTIVO");
		}
		liberarStockValidado(
				actual.getProducto().getProducto_ID(),
				actual.getUnidad_producto().intValue()
		);
		carritoProductoRepositorio.eliminarPorId(idCarritoProducto);
	}

	/** Usado al abandonar: libera SQL sin modificar todavía el documento. */
	public void liberarStockPorAbandono(CarritoProductoEntidad item) {
		liberarStockValidado(
				item.getProducto().getProducto_ID(), item.getUnidad_producto().intValue());
	}

	/** Usado al reactivar: reconstruye en PostgreSQL la reserva liberada. */
	public void reservarStockParaReactivacion(CarritoProductoEntidad item) {
		carritoProductoRepositorio.reservarStock(
				item.getProducto().getProducto_ID(), item.getUnidad_producto().intValue());
	}

	// Entradas: idCarrito
	// Salida: BigDecimal
	// Descripcion: calcula el subtotal del carrito.
	@Transactional(readOnly = true)
	public BigDecimal calcularSubtotal(Long idCarrito) {
		return carritoProductoRepositorio.calcularSubtotal(idCarrito);
	}
	
	private void actualizarMinimoB2B(CarritoProductoEntidad item, Long productoId) {
		item.setCantidadMinimaB2B(carritoMongoServicio.obtenerCantidadMinima(productoId));
	}

	private void reservarStockValidado(Long productoId, int cantidad) {
		try {
			carritoProductoRepositorio.reservarStock(productoId, cantidad);
		} catch (DataAccessException e) {
			String mensaje = e.getMostSpecificCause() != null
					? e.getMostSpecificCause().getMessage()
					: e.getMessage();
			if (mensaje != null && mensaje.toLowerCase().contains("stock insuficiente")) {
				throw new IllegalArgumentException("La cantidad solicitada excede el stock disponible");
			}
			throw e;
		}
	}

	private void liberarStockValidado(Long productoId, int cantidad) {
		try {
			carritoProductoRepositorio.liberarStock(productoId, cantidad);
		} catch (DataAccessException e) {
			String mensaje = e.getMostSpecificCause() != null
					? e.getMostSpecificCause().getMessage()
					: e.getMessage();
			if (mensaje != null && mensaje.toLowerCase().contains("stock reservado insuficiente")) {
				throw new IllegalArgumentException(
						"No se pudo liberar stock reservado para este producto. Actualiza el carrito e intenta nuevamente");
			}
			throw e;
		}
	}
}
