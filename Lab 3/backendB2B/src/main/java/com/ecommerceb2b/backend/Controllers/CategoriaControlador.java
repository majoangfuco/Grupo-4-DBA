package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.CategoriaEntidad;
import com.ecommerceb2b.backend.Services.CategoriaServicio;
import com.ecommerceb2b.backend.Config.JwtMiddlewareService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/categorias")
@CrossOrigin(origins = "*")
public class CategoriaControlador {

	private final CategoriaServicio categoriaServicio;
	private final JwtMiddlewareService jwtMiddlewareService;

	public CategoriaControlador(CategoriaServicio categoriaServicio,
								JwtMiddlewareService jwtMiddlewareService) {
		this.categoriaServicio = categoriaServicio;
		this.jwtMiddlewareService = jwtMiddlewareService;
	}

	// POST /api/categorias
	@PostMapping
	public ResponseEntity<?> crear(@RequestBody CategoriaEntidad categoria) {
		try {
			CategoriaEntidad creada = categoriaServicio.crearCategoria(categoria);
			return ResponseEntity.status(HttpStatus.CREATED).body(creada);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Error al crear categoria");
		}
	}

	// GET /api/categorias
	@GetMapping
	public ResponseEntity<?> listar(@RequestParam(defaultValue = "false") boolean incluirInactivas,
									@RequestHeader(value = "Authorization", required = false) String authHeader) {
		boolean incluirPermitidas = incluirInactivas && esAdmin(authHeader);
		return ResponseEntity.ok(categoriaServicio.listarCategorias(incluirPermitidas));
	}

	// GET /api/categorias/{id}
	@GetMapping("/{id}")
	public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(categoriaServicio.obtenerCategoriaPorId(id));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// PUT /api/categorias/{id}
	@PutMapping("/{id}")
	public ResponseEntity<?> actualizar(@PathVariable Long id,
										@RequestBody CategoriaEntidad categoria) {
		try {
			CategoriaEntidad actualizada = categoriaServicio.actualizarCategoria(id, categoria);
			return ResponseEntity.ok(actualizada);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// DELETE /api/categorias/{id}
	@DeleteMapping("/{id}")
	public ResponseEntity<?> eliminar(@PathVariable Long id) {
		try {
			categoriaServicio.eliminarCategoria(id);
			return ResponseEntity.ok("Categoria eliminada correctamente");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	// GET /api/categorias/buscar?nombre=...
	@GetMapping("/buscar")
	public ResponseEntity<?> buscarPorNombre(@RequestParam String nombre,
											 @RequestParam(defaultValue = "false") boolean incluirInactivas,
											 @RequestHeader(value = "Authorization", required = false) String authHeader) {
		try {
			boolean incluirPermitidas = incluirInactivas && esAdmin(authHeader);
			return ResponseEntity.ok(categoriaServicio.buscarPorNombre(nombre, incluirPermitidas));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	private boolean esAdmin(String authHeader) {
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return false;
		} 
		String token = authHeader.substring(7);
		if (!jwtMiddlewareService.validateToken(token)) {
			return false;
		}
		String rol = jwtMiddlewareService.getRolFromToken(token);
		return "ADMIN".equalsIgnoreCase(rol);
	}
}
