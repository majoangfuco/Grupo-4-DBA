package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Repository.LogisticaMapaRepositorio;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Rutas bajo /api/logistica/** restringidas a rol ADMIN en SecurityConfig.
// Consumido por el mapa "Logística y Cobertura" del frontend (Mapa-PaginaAdmin.vue).
// Los pines de almacén (AlmacenControlador) y el refresco de comunas
// (ComunaAdminControlador) viven aparte y no se tocan aquí.
@RestController
@RequestMapping("/api/logistica/mapa")
@CrossOrigin(origins = "*")
public class LogisticaMapaControlador {

    private final LogisticaMapaRepositorio repositorio;

    public LogisticaMapaControlador(LogisticaMapaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @GetMapping(value = "/comunas", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> comunas() {
        return ResponseEntity.ok(repositorio.comunasGeoJson());
    }

    @GetMapping(value = "/distritos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> distritos() {
        return ResponseEntity.ok(repositorio.distritosGeoJson());
    }

    // Contorno fijo de la RM (fila activa de zona_cobertura_entidad), para
    // dibujar el borde real de la unión de las 52 comunas en el mapa.
    @GetMapping(value = "/cobertura", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cobertura() {
        return ResponseEntity.ok(repositorio.coberturaGeoJson());
    }

    @PostMapping("/refrescar")
    public ResponseEntity<Map<String, String>> refrescar() {
        repositorio.refrescar();
        return ResponseEntity.ok(Map.of("mensaje", "Vistas de ventas por comuna y distrito refrescadas correctamente"));
    }
}
