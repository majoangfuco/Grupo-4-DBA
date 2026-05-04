package com.ecommerceb2b.backend.Services;


import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerceb2b.backend.Entities.CategoriaEntidad;
import com.ecommerceb2b.backend.Repository.CategoriaRepositorio;

@Service
public class CategoriaServicio {

	private final CategoriaRepositorio categoriaRepositorio;

	public CategoriaServicio(CategoriaRepositorio categoriaRepositorio) {
		this.categoriaRepositorio = categoriaRepositorio;
	}

	// Entradas: CategoriaEntidad 
	// Salida: CategoriaEntidad 
	// Descripcion: crea una nueva categoria.
	@Transactional
	public com.ecommerceb2b.backend.Entities.CategoriaEntidad crearCategoria(
			com.ecommerceb2b.backend.Entities.CategoriaEntidad categoria) {
		validarCategoria(categoria);
		categoria.setEstado_Categoria(true);
		int filasAfectadas = categoriaRepositorio.crear(categoria);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo crear la categoria");
		}
		return categoria;
	}

	// Entradas: idCategoria
	// Salida: CategoriaEntidad
	// Descripcion: obtiene una categoria por su id.
	@Transactional(readOnly = true)
	public com.ecommerceb2b.backend.Entities.CategoriaEntidad obtenerCategoriaPorId(Long idCategoria) {
		return categoriaRepositorio.encontrarPorId(idCategoria)
				.orElseThrow(() -> new NoSuchElementException(
					"Categoria no encontrada: " + idCategoria
				));
	}

	// Entradas: none
	// Salida: List<CategoriaEntidad> 
	// Descripcion: lista todas las categorias disponibles.
	@Transactional(readOnly = true)
	public java.util.List<com.ecommerceb2b.backend.Entities.CategoriaEntidad> listarCategorias() {
		return categoriaRepositorio.encontrarTodas();
	}

	@Transactional(readOnly = true)
	public java.util.List<com.ecommerceb2b.backend.Entities.CategoriaEntidad> listarCategorias(boolean incluirInactivas) {
		if (incluirInactivas) {
			return categoriaRepositorio.encontrarTodasIncluyendoInactivas();
		}
		return categoriaRepositorio.encontrarTodas();
	}

	// Entradas: idCategoria, CategoriaEntidad
	// Salida: CategoriaEntidad 
	// Descripcion: actualiza los datos de una categoria.
	@Transactional
	public com.ecommerceb2b.backend.Entities.CategoriaEntidad actualizarCategoria(
			Long idCategoria,
			com.ecommerceb2b.backend.Entities.CategoriaEntidad categoria) {
		CategoriaEntidad actual = obtenerCategoriaPorId(idCategoria);
		categoria.setCategoria_ID(actual.getCategoria_ID());
		categoria.setEstado_Categoria(actual.isEstado_Categoria());
		validarCategoria(categoria);

		int filasAfectadas = categoriaRepositorio.actualizar(categoria);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo actualizar la categoria: " + idCategoria);
		}
		return categoria;
	}

	// Entradas: idCategoria
	// Salida: void
	// Descripcion: Marca como inactiva la categoría.
	@Transactional
	public void eliminarCategoria(Long idCategoria) {
		obtenerCategoriaPorId(idCategoria);
		int filasAfectadas = categoriaRepositorio.eliminar(idCategoria);
		if (filasAfectadas == 0) {
			throw new IllegalStateException("No se pudo eliminar la categoria: " + idCategoria);
		}
	}

	// Entradas: nombre (parcial o exacto)
	// Salida: List<CategoriaEntidad> 
	// Descripcion: permite buscar categorias por nombre.
	@Transactional(readOnly = true)
	public java.util.List<com.ecommerceb2b.backend.Entities.CategoriaEntidad> buscarPorNombre(String nombre) {
		if (nombre == null || nombre.trim().isEmpty()) {
			throw new IllegalArgumentException("El nombre es obligatorio");
		}
		return categoriaRepositorio.buscarPorNombre(nombre.trim());
	}

	@Transactional(readOnly = true)
	public java.util.List<com.ecommerceb2b.backend.Entities.CategoriaEntidad> buscarPorNombre(
			String nombre,
			boolean incluirInactivas) {
		if (nombre == null || nombre.trim().isEmpty()) {
			throw new IllegalArgumentException("El nombre es obligatorio");
		}
		if (incluirInactivas) {
			return categoriaRepositorio.buscarPorNombreIncluyendoInactivas(nombre.trim());
		}
		return categoriaRepositorio.buscarPorNombre(nombre.trim());
	}

	private void validarCategoria(CategoriaEntidad categoria) {
		if (categoria == null) {
			throw new IllegalArgumentException("La categoria es obligatoria");
		}
		if (categoria.getNombre_Categoria() == null || categoria.getNombre_Categoria().trim().isEmpty()) {
			throw new IllegalArgumentException("El nombre de la categoria es obligatorio");
		}
	}
}
