package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.ProductoEntidad;
import com.ecommerceb2b.backend.Services.ProductoServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@CrossOrigin(origins = "*")
public class ProductoControlador {

    private final ProductoServicio productoServicio;

    public ProductoControlador(ProductoServicio productoServicio) {
        this.productoServicio = productoServicio;
    }

    // ─── CRUD ────────────────────────────────────────────────────────────────

    // POST /api/productos
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody ProductoEntidad producto) {
        try {
            ProductoEntidad creado = productoServicio.crearProducto(producto);
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al crear producto");
        }
    }

    // GET /api/productos
    @GetMapping
    public ResponseEntity<List<ProductoEntidad>> listar() {
        return ResponseEntity.ok(productoServicio.listarProductos());
    }

    // GET /api/productos/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(productoServicio.obtenerProductoPorId(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // PUT /api/productos/{id}
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                         @RequestBody ProductoEntidad producto) {
        try {
            ProductoEntidad actualizado = productoServicio.actualizarProducto(id, producto);
            return ResponseEntity.ok(actualizado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // DELETE /api/productos/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            productoServicio.eliminarProducto(id);
            return ResponseEntity.ok("Producto eliminado correctamente");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // ─── Búsquedas ───────────────────────────────────────────────────────────

    // GET /api/productos/buscar?termino=laptop   ← Req. 9
    @GetMapping("/buscar")
    public ResponseEntity<?> buscarPorNombreODescripcion(@RequestParam String termino) {
        try {
            List<ProductoEntidad> resultados = productoServicio.buscarPorNombreODescripcion(termino);
            return ResponseEntity.ok(resultados);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // GET /api/productos/sku/{sku}   ← Req. 8
    @GetMapping("/sku/{sku}")
    public ResponseEntity<?> buscarPorSku(@PathVariable String sku) {
        try {
            return productoServicio.buscarPorSku(sku)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    // GET /api/productos/categoria/{categoriaId}   ← Req. 7
    @GetMapping("/categoria/{categoriaId}")
    public ResponseEntity<?> listarPorCategoria(@PathVariable Long categoriaId) {
        try {
            return ResponseEntity.ok(productoServicio.listarPorCategoria(categoriaId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // GET /api/productos/{id}/stock   ← Req. 5
    @GetMapping("/{id}/stock")
    public ResponseEntity<?> obtenerStock(@PathVariable Long id) {
        try {
            int stock = productoServicio.obtenerStockPorId(id);
            return ResponseEntity.ok(stock);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // ─── Operaciones especiales ───────────────────────────────────────────────

    // PATCH /api/productos/{id}/stock   ← actualizar stock manualmente
    @PatchMapping("/{id}/stock")
    public ResponseEntity<?> actualizarStock(@PathVariable Long id,
                                              @RequestParam int nuevoStock) {
        try {
            return ResponseEntity.ok(productoServicio.actualizarStock(id, nuevoStock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // POST /api/productos/descuento?categoriaId=1&porcentaje=20   ← Req. 4
    @PostMapping("/descuento")
    public ResponseEntity<?> aplicarDescuento(@RequestParam int categoriaId,
                                               @RequestParam float porcentaje) {
        try {
            productoServicio.aplicarDescuentoMasivoPorCategoria(categoriaId, porcentaje);
            return ResponseEntity.ok("Descuento aplicado correctamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                "Error al aplicar descuento: " + e.getMessage()
            );
        }
    }
}