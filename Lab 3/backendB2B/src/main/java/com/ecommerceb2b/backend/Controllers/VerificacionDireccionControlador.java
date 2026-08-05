package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.VerificarDireccionRequestDto;
import com.ecommerceb2b.backend.Repository.CategoriaRepositorio;
import com.ecommerceb2b.backend.Repository.VerificacionDireccionRepositorio;
import com.ecommerceb2b.backend.Util.CoordenadasNormalizador;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Verificación de dirección "dry-run": dado un (lat,lng), responde si
 * quedaría dentro del área de cobertura, a qué comuna pertenecería, y si
 * cae en una zona residencial protegida — sin crear ni persistir nada.
 *
 * Reutiliza VerificacionDireccionRepositorio (cobertura/comuna/zona
 * protegida) y CategoriaRepositorio.listarRestringidasZonaResidencial()
 * (Parte 2, ya expuesto) para las categorías restringidas — sin duplicar SQL.
 */
@RestController
@RequestMapping("/api/admin/direccion")
@CrossOrigin(origins = "*")
public class VerificacionDireccionControlador {

    private final VerificacionDireccionRepositorio verificacionRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;

    public VerificacionDireccionControlador(VerificacionDireccionRepositorio verificacionRepositorio,
                                             CategoriaRepositorio categoriaRepositorio) {
        this.verificacionRepositorio = verificacionRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
    }

    // POST /api/admin/direccion/verificar
    // body: { "lat": -33.42, "lng": -70.61 } o GeoJSON Point
    // { "type": "Point", "coordinates": [-70.61, -33.42] } — solo consulta, no persiste nada.
    @PostMapping(value = "/verificar", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> verificar(@RequestBody VerificarDireccionRequestDto dto) {
        CoordenadasNormalizador.Coordenadas coords;
        try {
            coords = CoordenadasNormalizador.normalizar(
                    dto.getLat(), dto.getLng(), dto.getType(), dto.getCoordinates()
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        Double lat = coords.latitud();
        Double lng = coords.longitud();

        Map<String, Object> respuesta = new LinkedHashMap<>();

        boolean dentroCobertura = verificacionRepositorio.estaDentroCobertura(lat, lng);
        respuesta.put("dentroCobertura", dentroCobertura);

        if (!dentroCobertura) {
            // Fuera de cobertura: no tiene sentido calcular comuna/zona protegida.
            return ResponseEntity.ok(respuesta);
        }

        verificacionRepositorio.encontrarComunaPorPunto(lat, lng).ifPresent(comuna -> {
            respuesta.put("comunaId", comuna.get("id"));
            respuesta.put("comunaNombre", comuna.get("nombre"));
            respuesta.put("distritoPostal", comuna.get("distrito_postal"));
        });

        Optional<Map<String, Object>> zonaProtegida =
                verificacionRepositorio.encontrarZonaProtegidaPorPunto(lat, lng);
        boolean esZonaProtegida = zonaProtegida.isPresent();
        respuesta.put("zonaProtegida", esZonaProtegida);

        if (esZonaProtegida) {
            respuesta.put("nombreZonaProtegida", zonaProtegida.get().get("nombre_uv"));

            List<Map<String, Object>> categoriasRestringidas = categoriaRepositorio.listarRestringidasZonaResidencial()
                    .stream()
                    .map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", c.getCategoria_ID());
                        m.put("nombre", c.getNombre_Categoria());
                        return m;
                    })
                    .collect(Collectors.toList());
            respuesta.put("categoriasRestringidas", categoriasRestringidas);
        }

        return ResponseEntity.ok(respuesta);
    }
}
