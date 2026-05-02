package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.ProductoEntidad;
import com.ecommerceb2b.backend.Repository.ProductoRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ProductoServicio {

	private final ProductoRepositorio productoRepositorio;

	public ProductoServicio(ProductoRepositorio productoRepositorio) {
		this.productoRepositorio = productoRepositorio;
	}

	@Transactional
	public ProductoEntidad crearProducto(ProductoEntidad producto) {
		validarProducto(producto);
		validarSkuUnico(producto.getSku(), null);

		int filasAfectadas = productoRepositorio.crear(producto);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo crear el producto");
		}

		return producto;
	}

	@Transactional(readOnly = true)
	public List<ProductoEntidad> listarProductos() {
		return productoRepositorio.encontrarTodos();
	}

	@Transactional(readOnly = true)
	public Optional<ProductoEntidad> buscarPorSku(String sku) {
		if (sku == null || sku.trim().isEmpty()) {
			throw new IllegalArgumentException("El SKU es obligatorio");
		}
		return productoRepositorio.encontrarPorSku(sku.trim());
	}

	@Transactional(readOnly = true)
	public ProductoEntidad obtenerProductoPorId(Long productoId) {
		return productoRepositorio.encontrarPorId(productoId)
				.orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + productoId));
	}

	@Transactional(readOnly = true)
	public List<ProductoEntidad> listarPorCategoria(Long categoriaId) {
		if (categoriaId == null || categoriaId <= 0) {
			throw new IllegalArgumentException("La categoria es obligatoria");
		}
		return productoRepositorio.encontrarPorCategoria(categoriaId);
	}

	@Transactional(readOnly = true)
	public int obtenerStockPorId(Long productoId) {
		if (productoId == null || productoId <= 0) {
			throw new IllegalArgumentException("El producto es obligatorio");
		}
		return productoRepositorio.encontrarStockPorId(productoId);
	}

	@Transactional(readOnly = true)
	public List<ProductoEntidad> buscarPorNombreODescripcion(String termino) {
		if (termino == null || termino.trim().isEmpty()) {
			throw new IllegalArgumentException("El termino de busqueda es obligatorio");
		}
		return productoRepositorio.encontrarPorNombreODescripcion(termino.trim());
	}

	@Transactional
	public ProductoEntidad actualizarProducto(Long productoId, ProductoEntidad productoActualizado) {
		ProductoEntidad actual = obtenerProductoPorId(productoId);

		// Preservar el ID y el STOCK actuales para que no sean sobreescritos por la
		// actualización
		productoActualizado.setProducto_ID(actual.getProducto_ID());
		productoActualizado.setStock(actual.getStock());

		validarProducto(productoActualizado);
		validarSkuUnico(productoActualizado.getSku(), productoId);

		int filasAfectadas = productoRepositorio.actualizar(productoActualizado);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo actualizar el producto: " + productoId);
		}

		return productoActualizado;
	}

	@Transactional
	public void eliminarProducto(Long productoId) {
		obtenerProductoPorId(productoId);
		int filasAfectadas = productoRepositorio.borrarPorId(productoId);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo eliminar el producto: " + productoId);
		}
	}

	@Transactional
	public ProductoEntidad actualizarStock(Long productoId, int nuevoStock) {
		if (nuevoStock < 0) {
			throw new IllegalArgumentException("El stock no puede ser negativo");
		}

		ProductoEntidad producto = obtenerProductoPorId(productoId);
		producto.setStock(nuevoStock);

		int filasAfectadas = productoRepositorio.actualizar(producto);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo actualizar el stock del producto: " + productoId);
		}

		return producto;
	}

	@Transactional
	public void descontarStockParaOrden(Long productoId, int cantidad) {
		if (cantidad <= 0) {
			throw new IllegalArgumentException("La cantidad a descontar debe ser mayor a cero");
		}

		int stockActual = obtenerStockPorId(productoId);
		if (stockActual < cantidad) {
			throw new IllegalStateException("Stock insuficiente para el producto: " + productoId);
		}

		ProductoEntidad producto = obtenerProductoPorId(productoId);
		producto.setStock(stockActual - cantidad);
		int filasAfectadas = productoRepositorio.actualizar(producto);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo descontar stock para el producto: " + productoId);
		}
	}

	@Transactional
	public void aplicarDescuentoMasivoPorCategoria(Long categoriaId, float porcentaje) {
		productoRepositorio.aplicarDescuentoPorCategoria(categoriaId, porcentaje);
	}

	private void validarProducto(ProductoEntidad producto) {
		if (producto == null) {
			throw new IllegalArgumentException("El producto es obligatorio");
		}
		if (producto.getCategoria_ID() == null || producto.getCategoria_ID() <= 0) {
			throw new IllegalArgumentException("La categoria es obligatoria");
		}
		if (producto.getNombre_producto() == null || producto.getNombre_producto().trim().isEmpty()) {
			throw new IllegalArgumentException("El nombre del producto es obligatorio");
		}
		if (producto.getPrecio() == null || producto.getPrecio() < 0) {
			throw new IllegalArgumentException("El precio no puede ser nulo ni negativo");
		}
		if (producto.getStock() == null || producto.getStock() < 0) {
			throw new IllegalArgumentException("El stock no puede ser nulo ni negativo");
		}
		if (producto.getSku() == null || producto.getSku().trim().isEmpty()) {
			throw new IllegalArgumentException("El SKU es obligatorio");
		}
	}

	private void validarSkuUnico(String sku, Long productoIdActual) {
		Optional<ProductoEntidad> existente = productoRepositorio.encontrarPorSku(sku);
		if (existente.isPresent()) {
			boolean mismoProducto = productoIdActual != null
					&& productoIdActual.equals(existente.get().getProducto_ID());
			if (!mismoProducto) {
				throw new IllegalArgumentException("El SKU ya existe: " + sku);
			}
		}
	}
}
