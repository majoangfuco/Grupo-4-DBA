package com.ecommerceb2b.backend.Loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga rápida y sin red de las 52 comunas de la RM desde un GeoJSON local
 * (resources/data/comunas_rm.geojson), exportado una vez desde una base que
 * ya las tenía cargadas por ComunaOverpassLoader. Mismo motivo que
 * UnidadVecinalGeoJsonLoader: evitar que cada máquina nueva del equipo
 * tenga que volver a pegarle a la API pública de Overpass (compartida, con
 * rate-limit) al levantar un volumen de Postgres fresco — con varias
 * personas corriendo el loader en paralelo, Overpass empieza a responder
 * 429 seguido y una carga completa puede demorar minutos.
 *
 * ComunaOverpassLoader se mantiene intacto como respaldo (lo usa
 * StartupDataLoaderRunner solo para las comunas que este archivo no logre
 * cubrir) y como job manual vía POST /api/admin/comunas/cargar.
 */
@Component
public class ComunaGeoJsonLoader {

    private static final Logger log = LoggerFactory.getLogger(ComunaGeoJsonLoader.class);
    private static final String RUTA_GEOJSON = "data/comunas_rm.geojson";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public ComunaGeoJsonLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static class ResultadoCarga {
        public int totalLeidas;
        public int procesadas;
        public int descartadas;
    }

    /** Idempotente: UPSERT por nombre, igual que ComunaOverpassLoader.guardarComuna. */
    public ResultadoCarga cargar() {
        ResultadoCarga resultado = new ResultadoCarga();
        JsonNode root = leerGeoJson();
        JsonNode features = root.path("features");
        resultado.totalLeidas = features.size();
        log.info("Leídas {} features de {}", resultado.totalLeidas, RUTA_GEOJSON);

        for (JsonNode feature : features) {
            JsonNode props = feature.path("properties");
            String nombre = props.path("nombre").asText(null);
            String distrito = props.path("distrito_postal").asText(null);
            JsonNode geometryNode = feature.path("geometry");

            try {
                MultiPolygon geom = extraerMultiPolygon(geometryNode);
                if (geom == null || nombre == null) {
                    log.warn("Feature descartada del archivo local (geometría o nombre inválido): {}", props);
                    resultado.descartadas++;
                    continue;
                }
                guardarComuna(nombre, distrito, geom);
                resultado.procesadas++;
            } catch (Exception e) {
                log.error("Error procesando comuna '{}' del archivo local: {}", nombre, e.getMessage(), e);
                resultado.descartadas++;
            }
        }

        log.info("Carga local de comunas completada: {} procesadas, {} descartadas de {} leídas",
                resultado.procesadas, resultado.descartadas, resultado.totalLeidas);
        return resultado;
    }

    private JsonNode leerGeoJson() {
        try (InputStream is = new ClassPathResource(RUTA_GEOJSON).getInputStream()) {
            return objectMapper.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + RUTA_GEOJSON + " desde resources", e);
        }
    }

    private MultiPolygon extraerMultiPolygon(JsonNode geometryNode) {
        String tipo = geometryNode.path("type").asText();
        JsonNode coords = geometryNode.path("coordinates");
        return switch (tipo) {
            case "Polygon" -> {
                Polygon p = construirPoligono(coords);
                yield p == null ? null : geometryFactory.createMultiPolygon(new Polygon[]{p});
            }
            case "MultiPolygon" -> construirMultiPoligono(coords);
            default -> null;
        };
    }

    private MultiPolygon construirMultiPoligono(JsonNode coordsMulti) {
        if (!coordsMulti.isArray() || coordsMulti.isEmpty()) {
            return null;
        }
        List<Polygon> poligonos = new ArrayList<>();
        for (JsonNode coordsPoligono : coordsMulti) {
            Polygon p = construirPoligono(coordsPoligono);
            if (p != null) {
                poligonos.add(p);
            }
        }
        if (poligonos.isEmpty()) {
            return null;
        }
        return geometryFactory.createMultiPolygon(poligonos.toArray(new Polygon[0]));
    }

    /** coordsPoligono: array de anillos GeoJSON [ [ [lon,lat], ... ], [hueco...], ... ]. */
    private Polygon construirPoligono(JsonNode coordsPoligono) {
        if (!coordsPoligono.isArray() || coordsPoligono.isEmpty()) {
            return null;
        }
        LinearRing shell = null;
        List<LinearRing> holes = new ArrayList<>();
        for (int i = 0; i < coordsPoligono.size(); i++) {
            LinearRing anillo = construirAnillo(coordsPoligono.get(i));
            if (anillo == null) {
                continue;
            }
            if (i == 0) {
                shell = anillo;
            } else {
                holes.add(anillo);
            }
        }
        if (shell == null) {
            return null;
        }
        return geometryFactory.createPolygon(shell, holes.toArray(new LinearRing[0]));
    }

    private LinearRing construirAnillo(JsonNode anilloCoords) {
        if (!anilloCoords.isArray() || anilloCoords.size() < 4) {
            return null;
        }
        Coordinate[] coords = new Coordinate[anilloCoords.size()];
        for (int i = 0; i < anilloCoords.size(); i++) {
            JsonNode punto = anilloCoords.get(i);
            coords[i] = new Coordinate(punto.get(0).asDouble(), punto.get(1).asDouble());
        }
        return geometryFactory.createLinearRing(coords);
    }

    private void guardarComuna(String nombre, String distrito, MultiPolygon geom) {
        String wkt = new WKTWriter().write(geom);
        String sql = """
                INSERT INTO comuna_entidad (nombre, distrito_postal, geom)
                VALUES (?, ?, ST_Multi(ST_SetSRID(ST_GeomFromText(?), 4326)))
                ON CONFLICT (nombre) DO UPDATE
                    SET distrito_postal = EXCLUDED.distrito_postal,
                        geom = EXCLUDED.geom
                """;
        jdbc.update(sql, nombre, distrito, wkt);
    }
}
