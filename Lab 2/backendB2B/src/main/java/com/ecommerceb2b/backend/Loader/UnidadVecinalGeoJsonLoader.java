package com.ecommerceb2b.backend.Loader;

import com.ecommerceb2b.backend.Entities.UnidadVecinalEntidad;
import com.ecommerceb2b.backend.Repository.UnidadVecinalRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Job de ejecución puntual (NO se ejecuta en el arranque normal de la app).
 * Lee el shapefile de Unidades Vecinales (Ministerio de Desarrollo Social y
 * Familia, RM, agosto 2024) ya convertido a GeoJSON en
 * resources/data/unidades_vecinales_rm.geojson, y lo persiste en
 * unidad_vecinal_entidad vía UnidadVecinalRepositorio.
 *
 * Se dispara solo vía POST /api/admin/unidades-vecinales/cargar (rol ADMIN)
 * — ver Controllers/UnidadVecinalAdminControlador.
 *
 * A diferencia de ComunaOverpassLoader (que compone SQL crudo directamente),
 * este loader usa exclusivamente UnidadVecinalRepositorio para leer/escribir
 * — no hay JdbcTemplate paralelo aquí.
 */
@Component
public class UnidadVecinalGeoJsonLoader {

    private static final Logger log = LoggerFactory.getLogger(UnidadVecinalGeoJsonLoader.class);

    private static final String RUTA_GEOJSON = "data/unidades_vecinales_rm.geojson";

    private final UnidadVecinalRepositorio repositorio;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public UnidadVecinalGeoJsonLoader(UnidadVecinalRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public static class ResultadoCarga {
        public int totalLeidas;
        public int procesadas;
        public int descartadas;
    }

    /**
     * Punto de entrada del job. Idempotente por (comuna_id, codigo_uv) vía
     * UnidadVecinalRepositorio.insertarOActualizar (UPSERT).
     */
    public ResultadoCarga cargar() {
        ResultadoCarga resultado = new ResultadoCarga();

        JsonNode root = leerGeoJson();
        JsonNode features = root.path("features");
        resultado.totalLeidas = features.size();
        log.info("Leídas {} features de {}", resultado.totalLeidas, RUTA_GEOJSON);

        for (JsonNode feature : features) {
            JsonNode props = feature.path("properties");
            String codigoUv = props.path("t_id_uv_ca").asText(null);
            String nombreUv = props.path("t_uv_nom").asText("SIN NOMBRE");
            JsonNode geometryNode = feature.path("geometry");
            String tipoGeom = geometryNode.path("type").asText();

            try {
                Polygon poligono = extraerPoligono(geometryNode, tipoGeom, nombreUv, codigoUv);
                if (poligono == null) {
                    log.warn("UV código '{}' ({}): geometría vacía, inválida sin reparación posible, o tipo no soportado ('{}'); se descarta",
                            codigoUv, nombreUv, tipoGeom);
                    resultado.descartadas++;
                    continue;
                }

                String wkt = new WKTWriter().write(poligono);

                // Matching por geometría (ST_Within del centroide), nunca por
                // t_com_nom — ver comentario en UnidadVecinalRepositorio.
                Optional<Long> comunaId = repositorio.encontrarComunaIdPorCentroide(wkt);
                if (comunaId.isEmpty()) {
                    // Caso esperado: UVs de comunas fuera de la RM o zonas
                    // "SIN DEF COMUNAL" (territorio rural sin comuna asociada),
                    // presentes en el archivo por el filtro de bounding box.
                    resultado.descartadas++;
                    continue;
                }

                UnidadVecinalEntidad uv = new UnidadVecinalEntidad();
                uv.setComunaId(comunaId.get());
                uv.setCodigoUv(codigoUv);
                uv.setNombreUv(nombreUv);
                uv.setGeom(wkt);
                repositorio.insertarOActualizar(uv);
                resultado.procesadas++;

            } catch (Exception e) {
                log.error("Error inesperado procesando UV código '{}' ({}): {}", codigoUv, nombreUv, e.getMessage(), e);
                resultado.descartadas++;
            }
        }

        log.info("=== Resumen carga unidades vecinales ===");
        log.info("  Total de features leídas del archivo: {}", resultado.totalLeidas);
        log.info("  Insertadas/actualizadas: {}", resultado.procesadas);
        log.info("  Descartadas por no pertenecer a ninguna de las 52 comunas de cobertura " +
                "(esperado: zonas 'SIN DEF COMUNAL' / territorio rural, y UVs de comunas fuera de la RM): {}",
                resultado.descartadas);
        if (resultado.procesadas + resultado.descartadas != resultado.totalLeidas) {
            log.error("INCONSISTENCIA: procesadas ({}) + descartadas ({}) != total leídas ({})",
                    resultado.procesadas, resultado.descartadas, resultado.totalLeidas);
        }

        return resultado;
    }

    private JsonNode leerGeoJson() {
        try (InputStream is = new ClassPathResource(RUTA_GEOJSON).getInputStream()) {
            return objectMapper.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + RUTA_GEOJSON + " desde resources", e);
        }
    }

    /**
     * Arma el polígono final para una feature, cubriendo ambas ramas del
     * archivo (2302 Polygon + 8 MultiPolygon) y reparando con buffer(0) si
     * la geometría queda inválida, mismo criterio que ComunaOverpassLoader.
     */
    private Polygon extraerPoligono(JsonNode geometryNode, String tipoGeom, String nombreUv, String codigoUv) {
        Polygon poligono = switch (tipoGeom) {
            case "Polygon" -> construirPoligono(geometryNode.path("coordinates"));
            case "MultiPolygon" -> construirDesdeMultiPolygon(geometryNode.path("coordinates"), nombreUv, codigoUv);
            default -> null;
        };
        if (poligono == null) {
            return null;
        }
        return repararSiInvalido(poligono, codigoUv);
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

    /**
     * Para features MultiPolygon (8 en el archivo, todas zonas "SIN DEF
     * COMUNAL" con 2-9 partes disjuntas): se queda con el polígono de mayor
     * área y loguea cuántas partes se descartaron.
     */
    private Polygon construirDesdeMultiPolygon(JsonNode coordsMultiPoligono, String nombreUv, String codigoUv) {
        if (!coordsMultiPoligono.isArray() || coordsMultiPoligono.isEmpty()) {
            return null;
        }
        Polygon mayor = null;
        double mayorArea = -1;
        int totalPartes = coordsMultiPoligono.size();

        for (JsonNode coordsPoligono : coordsMultiPoligono) {
            Polygon candidato = construirPoligono(coordsPoligono);
            if (candidato == null) {
                continue;
            }
            double area = candidato.getArea();
            if (area > mayorArea) {
                mayorArea = area;
                mayor = candidato;
            }
        }

        if (mayor != null && totalPartes > 1) {
            log.warn("UV código '{}' ({}): MultiPolygon con {} partes, se descartaron {} y se conservó la de mayor área",
                    codigoUv, nombreUv, totalPartes, totalPartes - 1);
        }
        return mayor;
    }

    private Polygon repararSiInvalido(Polygon poligono, String codigoUv) {
        if (poligono.isValid()) {
            return poligono;
        }
        log.warn("Geometría inválida para UV código '{}', aplicando buffer(0) para reparar", codigoUv);
        Geometry reparada = poligono.buffer(0);

        if (reparada instanceof Polygon p) {
            return p;
        }
        if (reparada instanceof MultiPolygon mp) {
            Polygon mayor = null;
            double mayorArea = -1;
            for (int i = 0; i < mp.getNumGeometries(); i++) {
                Polygon parte = (Polygon) mp.getGeometryN(i);
                if (parte.getArea() > mayorArea) {
                    mayorArea = parte.getArea();
                    mayor = parte;
                }
            }
            return mayor;
        }

        log.error("Geometría reparada de UV código '{}' no es un polígono válido (tipo: {})",
                codigoUv, reparada.getGeometryType());
        return null;
    }
}
