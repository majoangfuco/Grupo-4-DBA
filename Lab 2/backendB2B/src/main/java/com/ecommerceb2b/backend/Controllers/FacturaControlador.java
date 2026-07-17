package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Services.FacturaPdfServicio;
import com.ecommerceb2b.backend.Services.FacturaServicio;
import com.lowagie.text.DocumentException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/facturas")
@CrossOrigin(origins = "*")
public class FacturaControlador {

    private final FacturaServicio servicio;
    private final FacturaPdfServicio pdfServicio;
    private final com.ecommerceb2b.backend.Config.JwtMiddlewareService jwtMiddlewareService;

    public FacturaControlador(FacturaServicio servicio, FacturaPdfServicio pdfServicio, com.ecommerceb2b.backend.Config.JwtMiddlewareService jwtMiddlewareService) {
        this.servicio = servicio;
        this.pdfServicio = pdfServicio;
        this.jwtMiddlewareService = jwtMiddlewareService;
    }

    @GetMapping
    public ResponseEntity<List<FacturaEntidad>> obtenerTodas(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(403).build();
        }
        String token = authHeader.substring(7);
        Long usuarioId = jwtMiddlewareService.getUsuarioIdFromToken(token);
        String rol = jwtMiddlewareService.getRolFromToken(token);

        List<FacturaEntidad> facturas = servicio.obtenerTodas();

        // Filter for CLIENTE
        if ("CLIENTE".equals(rol)) {
            facturas = facturas.stream()
                    .filter(f -> f.getUsuarioId().equals(usuarioId))
                    .toList();
        }

        return ResponseEntity.ok(facturas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaEntidad> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(403).build();
        }
        String token = authHeader.substring(7);
        Long usuarioId = jwtMiddlewareService.getUsuarioIdFromToken(token);
        String rol = jwtMiddlewareService.getRolFromToken(token);

        FacturaEntidad factura = servicio.obtenerPorId(id);

        // Check if user can access this factura
        if ("CLIENTE".equals(rol) && !factura.getUsuarioId().equals(usuarioId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(factura);
    }

    // Endpoint 10 del enunciado
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<FacturaEntidad>> obtenerPorUsuario(@PathVariable Long usuarioId, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(403).build();
        }
        String token = authHeader.substring(7);
        Long currentUsuarioId = jwtMiddlewareService.getUsuarioIdFromToken(token);
        String rol = jwtMiddlewareService.getRolFromToken(token);

        // Check if user can access this user's facturas
        if ("CLIENTE".equals(rol) && !usuarioId.equals(currentUsuarioId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(servicio.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/orden/{ordenId}")
    public ResponseEntity<FacturaEntidad> obtenerPorOrden(@PathVariable Long ordenId, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(403).build();
        }
        String token = authHeader.substring(7);
        Long usuarioId = jwtMiddlewareService.getUsuarioIdFromToken(token);
        String rol = jwtMiddlewareService.getRolFromToken(token);

        Optional<FacturaEntidad> facturaOpt = servicio.obtenerPorOrden(ordenId);
        if (facturaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FacturaEntidad factura = facturaOpt.get();

        // Check if user can access this factura
        if ("CLIENTE".equals(rol) && !factura.getUsuarioId().equals(usuarioId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(factura);
    }

    @GetMapping("/orden/{ordenId}/descargar")
    public ResponseEntity<byte[]> descargarPorOrden(@PathVariable Long ordenId, HttpServletRequest request) throws DocumentException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(403).build();
        }
        String token = authHeader.substring(7);
        Long usuarioId = jwtMiddlewareService.getUsuarioIdFromToken(token);
        String rol = jwtMiddlewareService.getRolFromToken(token);

        Optional<FacturaEntidad> facturaOpt = servicio.obtenerPorOrden(ordenId);
        if (facturaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FacturaEntidad factura = facturaOpt.get();

        // Check if user can access this factura
        if ("CLIENTE".equals(rol) && !factura.getUsuarioId().equals(usuarioId)) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdf = pdfServicio.generarPdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "factura-" + factura.getFactura_ID() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, HttpServletRequest request) throws DocumentException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(403).build();
        }
        String token = authHeader.substring(7);
        Long usuarioId = jwtMiddlewareService.getUsuarioIdFromToken(token);
        String rol = jwtMiddlewareService.getRolFromToken(token);

        FacturaEntidad factura = servicio.obtenerPorId(id);

        // Check if user can access this factura
        if ("CLIENTE".equals(rol) && !factura.getUsuarioId().equals(usuarioId)) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdf = pdfServicio.generarPdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "factura-" + id + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}