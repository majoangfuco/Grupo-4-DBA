package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.CheckoutPedidoDto;
import com.ecommerceb2b.backend.Entities.OrdenesEntidad;
import com.ecommerceb2b.backend.Services.OrdenesServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/ordenes")
@CrossOrigin("localhost:5174")
public class OrdenesControlador {

    private final OrdenesServicio ordenesServicio;

    public OrdenesControlador(OrdenesServicio ordenesServicio) {
        this.ordenesServicio = ordenesServicio;
    }

    // ─── CRUD ────────────────────────────────────────────────────────────────

    // POST /api/ordenes
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody OrdenesEntidad orden) {
        try {
            OrdenesEntidad creada = ordenesServicio.crearOrden(orden);
            return ResponseEntity.status(HttpStatus.CREATED).body(creada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al crear la orden");
        }
    }

    // POST /api/ordenes/solicitar/{carritoId}
    @PostMapping("/solicitar/{carritoId}")
    public ResponseEntity<?> solicitarOrden(@PathVariable Long carritoId, @RequestBody CheckoutPedidoDto pedido) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ordenesServicio.solicitarOrdenAtomica(carritoId, pedido));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al procesar la solicitud de orden: " + e.getMessage());
        }
    }

    // GET /api/ordenes
    @GetMapping
    public ResponseEntity<List<OrdenesEntidad>> listar() {
        return ResponseEntity.ok(ordenesServicio.listarOrdenes());
    }

    // GET /api/ordenes/vista/admin (retorna OrdenesEntidad con rut_empresa)
    @GetMapping("/vista/admin")
    public ResponseEntity<List<OrdenesEntidad>> listarVistaAdmin() {
        return ResponseEntity.ok(ordenesServicio.listarOrdenesConRut());
    }

    // GET /api/ordenes/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ordenesServicio.obtenerOrdenPorId(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // PUT /api/ordenes/{id}
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                         @RequestBody OrdenesEntidad orden) {
        try {
            return ResponseEntity.ok(ordenesServicio.actualizarOrden(id, orden));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // DELETE /api/ordenes/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            ordenesServicio.eliminarOrden(id);
            return ResponseEntity.ok("Orden eliminada correctamente");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // ─── Búsquedas ───────────────────────────────────────────────────────────

    // GET /api/ordenes/usuario/{usuarioId}   ← Req. 2
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> listarPorUsuario(@PathVariable Long usuarioId) {
        try {
            return ResponseEntity.ok(ordenesServicio.listarPorUsuario(usuarioId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al listar órdenes");
        }
    }

    // GET /api/ordenes/estado?valor=PENDIENTE
    @GetMapping("/estado")
    public ResponseEntity<?> listarPorEstado(@RequestParam String valor) {
        try {
            return ResponseEntity.ok(ordenesServicio.listarPorEstado(valor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al listar órdenes");
        }
    }

    // GET /api/ordenes/fecha?desde=2024-01-01
    @GetMapping("/fecha")
    public ResponseEntity<?> listarPorFecha(@RequestParam Date desde) {
        try {
            return ResponseEntity.ok(ordenesServicio.listarPorFecha(desde));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al listar órdenes");
        }
    }

    // ─── Cambios de estado ───────────────────────────────────────────────────

    // PATCH /api/ordenes/{id}/aprobar   ← Req. 2 Admin + dispara Trigger 2
    @PatchMapping("/{id}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ordenesServicio.aprobarOrden(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // PATCH /api/ordenes/{id}/cancelar
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ordenesServicio.cancelarOrden(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}