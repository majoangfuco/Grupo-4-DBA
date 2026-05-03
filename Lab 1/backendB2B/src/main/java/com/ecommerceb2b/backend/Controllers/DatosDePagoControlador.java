package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.DatosDePagoEntidad;
import com.ecommerceb2b.backend.Services.DatosDePagoServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datos-pago")
@CrossOrigin(origins = "*")
public class DatosDePagoControlador {

    private final DatosDePagoServicio datosDePagoServicio;

    public DatosDePagoControlador(DatosDePagoServicio datosDePagoServicio) {
        this.datosDePagoServicio = datosDePagoServicio;
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<DatosDePagoEntidad>> obtenerPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(datosDePagoServicio.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> obtenerPorId(@PathVariable Long id) {
        return datosDePagoServicio.obtenerPorId(id)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Datos de pago no encontrados")));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody DatosDePagoEntidad datosDePago) {
        if (datosDePago.getUsuario_ID() == null || datosDePago.getMetodo_Pago() == null || datosDePago.getNumero_Tarjeta() == null || datosDePago.getFecha_Expiracion() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Todos los campos son requeridos"));
        }

        datosDePagoServicio.guardar(datosDePago);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mensaje", "Datos de pago guardados correctamente"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id, @RequestBody DatosDePagoEntidad datosDePago) {
        if (datosDePago.getMetodo_Pago() == null || datosDePago.getNumero_Tarjeta() == null || datosDePago.getFecha_Expiracion() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Todos los campos son requeridos"));
        }

        if (datosDePagoServicio.obtenerPorId(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Datos de pago no encontrados"));
        }

        datosDePago.setDatos_Pago_ID(id);
        datosDePagoServicio.actualizar(datosDePago);
        return ResponseEntity.ok(Map.of("mensaje", "Datos de pago actualizados correctamente"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        if (datosDePagoServicio.obtenerPorId(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Datos de pago no encontrados"));
        }

        datosDePagoServicio.eliminar(id);
        return ResponseEntity.ok(Map.of("mensaje", "Datos de pago eliminados correctamente"));
    }
}
