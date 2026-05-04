package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Services.FacturaPdfServicio;
import com.ecommerceb2b.backend.Services.FacturaServicio;
import com.lowagie.text.DocumentException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facturas")
@CrossOrigin(origins = "*")
public class FacturaControlador {

    private final FacturaServicio servicio;
    private final FacturaPdfServicio pdfServicio;

    public FacturaControlador(FacturaServicio servicio, FacturaPdfServicio pdfServicio) {
        this.servicio = servicio;
        this.pdfServicio = pdfServicio;
    }

    @GetMapping
    public ResponseEntity<List<FacturaEntidad>> obtenerTodas() {
        return ResponseEntity.ok(servicio.obtenerTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaEntidad> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(servicio.obtenerPorId(id));
    }

    // Endpoint 10 del enunciado
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<FacturaEntidad>> obtenerPorUsuario(
            @PathVariable Long usuarioId) {
        return ResponseEntity.ok(servicio.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/orden/{ordenId}")
    public ResponseEntity<FacturaEntidad> obtenerPorOrden(@PathVariable Long ordenId) {
        return ResponseEntity.ok(servicio.obtenerPorOrden(ordenId));
    }

    @GetMapping("/orden/{ordenId}/descargar")
    public ResponseEntity<byte[]> descargarPorOrden(@PathVariable Long ordenId) throws DocumentException {
        FacturaEntidad factura = servicio.obtenerPorOrden(ordenId);
        byte[] pdf = pdfServicio.generarPdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "factura-" + factura.getFactura_ID() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) throws DocumentException {
        FacturaEntidad factura = servicio.obtenerPorId(id);
        byte[] pdf = pdfServicio.generarPdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "factura-" + id + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}