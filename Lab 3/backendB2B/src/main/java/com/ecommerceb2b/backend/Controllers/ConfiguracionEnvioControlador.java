package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.ConfiguracionEnvioEntidad;
import com.ecommerceb2b.backend.Repository.ConfiguracionEnvioRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config/envio")
@CrossOrigin(origins = "*")
public class ConfiguracionEnvioControlador {

    private final ConfiguracionEnvioRepositorio repositorio;

    public ConfiguracionEnvioControlador(ConfiguracionEnvioRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    // Tarifa de envío actual (valor por km). Accesible a cualquier usuario autenticado.
    @GetMapping
    public ResponseEntity<ConfiguracionEnvioEntidad> obtener() {
        return ResponseEntity.ok(repositorio.obtener());
    }

    // Solo administrador (protegido en SecurityConfig). Fija el valor por km.
    @PutMapping
    public ResponseEntity<?> actualizar(@RequestBody Map<String, Object> body) {
        Object valor = body.get("valorKm");
        if (valor == null) {
            valor = body.get("valor_km");
        }
        if (valor == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "valorKm es obligatorio"));
        }
        double valorKm;
        try {
            valorKm = Double.parseDouble(valor.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "valorKm debe ser numérico"));
        }
        if (valorKm < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "valorKm no puede ser negativo"));
        }
        return ResponseEntity.ok(repositorio.actualizarValorKm(valorKm));
    }
}
