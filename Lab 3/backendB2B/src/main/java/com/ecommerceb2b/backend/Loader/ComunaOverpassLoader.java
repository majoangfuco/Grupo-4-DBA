package com.ecommerceb2b.backend.Loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Job de ejecución única (NO se ejecuta en el arranque normal de la app).
 * Descarga los polígonos administrativos reales de las 52 comunas de la
 * Región Metropolitana desde Overpass API y los persiste en comuna_entidad.
 *
 * Se dispara solo vía POST /api/admin/comunas/cargar (rol ADMIN) —
 * ver Controllers/ComunaAdminControlador.
 */
@Component
public class ComunaOverpassLoader {

    private static final Logger log = LoggerFactory.getLogger(ComunaOverpassLoader.class);

    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    // admin_level real usado por OSM para comunas chilenas: 2=país, 4=región,
    // 6=provincia, 8=comuna. El enunciado original pedía admin_level=6, pero
    // eso corresponde a "provincia" en Chile, no a comuna — se corrige a 8.
    private static final int ADMIN_LEVEL_COMUNA = 8;

    private static final int MAX_REINTENTOS = 3;
    private static final long ESPERA_ENTRE_REINTENTOS_MS = 5000;
    private static final long ESPERA_ENTRE_COMUNAS_MS = 1500; // buena vecindad con la API pública
    private static final long ESPERA_RATE_LIMIT_MS = 30000; // backoff largo específico ante 429

    // Lista cerrada y exacta: 52 comunas de la Región Metropolitana.
    private static final Map<String, String> COMUNAS_DISTRITO = crearMapaComunas();

    // Alias de búsqueda para comunas cuyo "name" en OSM no coincide con el
    // nombre oficial SUBDERE (verificado manualmente contra Overpass/Wikidata).
    // El alias SOLO se usa para construir la consulta; lo que se persiste en
    // comuna_entidad.nombre siempre es la clave de COMUNAS_DISTRITO (nombre
    // oficial), nunca el alias.
    private static final Map<String, String> ALIAS_BUSQUEDA_OSM = Map.of(
            "Til Til", "Tiltil"
    );

    private static Map<String, String> crearMapaComunas() {
        Map<String, String> m = new LinkedHashMap<>();
        // Distrito 7xx (8 comunas)
        for (String n : List.of("Providencia", "Las Condes", "Vitacura", "Lo Barnechea",
                "Ñuñoa", "La Reina", "Macul", "Peñalolén")) {
            m.put(n, "7xx");
        }
        // Distrito 8xx (26 comunas)
        for (String n : List.of("Santiago", "Cerrillos", "Cerro Navia", "Conchalí", "El Bosque",
                "Estación Central", "Huechuraba", "Independencia", "La Cisterna", "La Florida",
                "La Granja", "La Pintana", "Lo Espejo", "Lo Prado", "Maipú", "Pedro Aguirre Cerda",
                "Pudahuel", "Quilicura", "Quinta Normal", "Recoleta", "Renca", "San Joaquín",
                "San Miguel", "San Ramón", "San Bernardo", "Puente Alto")) {
            m.put(n, "8xx");
        }
        // Distrito 9xx (18 comunas)
        for (String n : List.of("Colina", "Lampa", "Til Til", "Pirque", "San José de Maipo",
                "Buin", "Calera de Tango", "Paine", "Melipilla", "Alhué", "Curacaví",
                "María Pinto", "San Pedro", "Talagante", "El Monte", "Isla de Maipo",
                "Padre Hurtado", "Peñaflor")) {
            m.put(n, "9xx");
        }
        if (m.size() != 52) {
            throw new IllegalStateException("La lista cerrada de comunas RM debe tener 52 entradas, tiene " + m.size());
        }
        return Collections.unmodifiableMap(m);
    }

    private final JdbcTemplate jdbc;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public ComunaOverpassLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public static class ResultadoCarga {
        public int totalCargadas;
        public Map<String, Integer> cargadasPorDistrito = new LinkedHashMap<>();
        public List<String> comunasFallidas = new ArrayList<>();
        public boolean completoOk;
    }

    /**
     * Punto de entrada del job. Idempotente: usa UPSERT por nombre de comuna.
     * Omite las comunas que ya están en comuna_entidad (no las vuelve a pedir
     * a Overpass) para que un reintento tras una carga parcial solo golpee la
     * API pública por las que realmente faltan.
     */
    public ResultadoCarga cargarComunas() {
        ResultadoCarga resultado = new ResultadoCarga();

        Set<String> yaCargadas = new HashSet<>(
                jdbc.queryForList("SELECT nombre FROM comuna_entidad WHERE ST_NPoints(geom) > 10", String.class));
        if (!yaCargadas.isEmpty()) {
            log.info("{} comuna(s) ya presentes en comuna_entidad, se omiten: {}", yaCargadas.size(), yaCargadas);
        }

        for (Map.Entry<String, String> entry : COMUNAS_DISTRITO.entrySet()) {
            String nombre = entry.getKey();
            String distrito = entry.getValue();

            if (yaCargadas.contains(nombre)) {
                continue;
            }

            log.info("Cargando comuna '{}' ({})...", nombre, distrito);
            try {
                MultiPolygon geom = descargarConReintentos(nombre);
                if (geom == null) {
                    log.error("No se pudo obtener geometría para la comuna '{}' tras {} intentos", nombre, MAX_REINTENTOS);
                    resultado.comunasFallidas.add(nombre);
                    continue;
                }

                guardarComuna(nombre, distrito, geom);
                resultado.cargadasPorDistrito.merge(distrito, 1, Integer::sum);
                resultado.totalCargadas++;
                log.info("Comuna cargada OK: {} ({}) - {} polígono(s)", nombre, distrito, geom.getNumGeometries());

            } catch (Exception e) {
                log.error("Error inesperado cargando la comuna '{}': {}", nombre, e.getMessage(), e);
                resultado.comunasFallidas.add(nombre);
            }

            dormir(ESPERA_ENTRE_COMUNAS_MS);
        }

        int totalEnBd = yaCargadas.size() + resultado.totalCargadas;
        resultado.completoOk = totalEnBd == 52 && resultado.comunasFallidas.isEmpty();

        log.info("=== Resumen carga comunas RM ===");
        resultado.cargadasPorDistrito.forEach((d, c) -> log.info("  Distrito {}: {} comunas nuevas", d, c));
        log.info("  Cargadas en esta corrida: {}, ya existentes: {}, total en BD: {}/52", resultado.totalCargadas, yaCargadas.size(), totalEnBd);
        if (!resultado.comunasFallidas.isEmpty()) {
            log.error("  Comunas fallidas ({}): {}", resultado.comunasFallidas.size(), resultado.comunasFallidas);
        }
        if (!resultado.completoOk) {
            log.error("CARGA INCOMPLETA: se esperaban 52 comunas y hay {} en la BD", totalEnBd);
        }

        return resultado;
    }

    private MultiPolygon descargarConReintentos(String nombreComuna) {
        for (int intento = 1; intento <= MAX_REINTENTOS; intento++) {
            try {
                String json = consultarOverpass(nombreComuna, ADMIN_LEVEL_COMUNA);
                MultiPolygon geom = extraerMultiPolygon(json, nombreComuna);
                if (geom != null) {
                    return geom;
                }

                // Fallback: algunas comunas pueden estar mal etiquetadas con otro
                // admin_level en OSM; se reintenta sin esa restricción, siempre
                // acotado al área de la Región Metropolitana (ISO3166-2=CL-RM).
                log.warn("Comuna '{}': sin resultados con admin_level={}, reintentando sin filtro de nivel", nombreComuna, ADMIN_LEVEL_COMUNA);
                json = consultarOverpass(nombreComuna, null);
                geom = extraerMultiPolygon(json, nombreComuna);
                if (geom != null) {
                    return geom;
                }

                // Fallback: alias conocido cuando el "name" oficial en OSM difiere
                // del nombre SUBDERE (caso real: Til Til está tageada en OSM como
                // name=Tiltil, verificado contra Overpass/Wikidata). El resultado
                // se sigue guardando bajo el nombre oficial (nombreComuna), nunca
                // bajo el alias — ver guardarComuna().
                String alias = ALIAS_BUSQUEDA_OSM.get(nombreComuna);
                if (alias != null) {
                    log.warn("Comuna '{}': sin resultados por nombre oficial, reintentando con alias conocido '{}'", nombreComuna, alias);
                    json = consultarOverpass(alias, null);
                    geom = extraerMultiPolygon(json, nombreComuna);
                    if (geom != null) {
                        return geom;
                    }
                }

                // Fallback final (blindaje genérico): variantes de escritura no
                // catalogadas en ALIAS_BUSQUEDA_OSM (espacios de más/menos, etc.)
                // vía regex case-insensitive sobre "name", tolerando espacios.
                log.warn("Comuna '{}': sin resultados exactos, reintentando con regex de nombre aproximado", nombreComuna);
                json = consultarOverpassPorNombreAproximado(nombreComuna);
                geom = extraerMultiPolygon(json, nombreComuna);
                if (geom != null) {
                    return geom;
                }

            } catch (RateLimitException e) {
                log.warn("Intento {}/{} fallido para '{}' por rate limit, backoff largo: {}", intento, MAX_REINTENTOS, nombreComuna, e.getMessage());
                if (intento < MAX_REINTENTOS) {
                    dormir(ESPERA_RATE_LIMIT_MS);
                }
                continue;
            } catch (Exception e) {
                log.warn("Intento {}/{} fallido para '{}': {}", intento, MAX_REINTENTOS, nombreComuna, e.getMessage());
            }

            if (intento < MAX_REINTENTOS) {
                dormir(ESPERA_ENTRE_REINTENTOS_MS);
            }
        }
        return null;
    }

    private String consultarOverpass(String nombreComuna, Integer adminLevel) throws Exception {
        String filtroNivel = adminLevel != null ? "[\"admin_level\"=\"" + adminLevel + "\"]" : "";
        String nombreEscapado = nombreComuna.replace("\"", "\\\"");

        String query = """
                [out:json][timeout:90];
                area["ISO3166-2"="CL-RM"]->.rm;
                relation["boundary"="administrative"]%s["name"="%s"](area.rm);
                out body geom;
                """.formatted(filtroNivel, nombreEscapado);

        return ejecutarQuery(query, nombreComuna);
    }

    /**
     * Último recurso: match por regex case-insensitive sobre "name", tolerando
     * cualquier cantidad de espacios entre palabras (cubre variantes tipo
     * "Til Til" vs "Tiltil" sin necesidad de catalogar cada caso en
     * ALIAS_BUSQUEDA_OSM).
     */
    private String consultarOverpassPorNombreAproximado(String nombreComuna) throws Exception {
        String[] palabras = nombreComuna.trim().split("\\s+");
        StringBuilder patron = new StringBuilder("^");
        for (int i = 0; i < palabras.length; i++) {
            if (i > 0) {
                patron.append("\\s*");
            }
            patron.append(escaparRegex(palabras[i]));
        }
        patron.append("$");

        String query = """
                [out:json][timeout:90];
                area["ISO3166-2"="CL-RM"]->.rm;
                relation["boundary"="administrative"]["name"~"%s",i](area.rm);
                out body geom;
                """.formatted(patron);

        return ejecutarQuery(query, nombreComuna);
    }

    private static String escaparRegex(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            if ("\\^$.|?*+()[]{}\"".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String ejecutarQuery(String query, String nombreComuna) throws Exception {
        String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OVERPASS_URL))
                .timeout(Duration.ofSeconds(100))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            throw new RateLimitException("Overpass respondió 429 (rate limit) para '" + nombreComuna + "'");
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("Overpass respondió " + response.statusCode() + " para '" + nombreComuna + "'");
        }
        return response.body();
    }

    /** Señala 429 específicamente para que el retry haga un backoff más largo que el de un error genérico. */
    private static class RateLimitException extends RuntimeException {
        RateLimitException(String message) { super(message); }
    }

    /**
     * Ensambla el/los polígono(s) de una relation de OSM a partir de sus
     * miembros "way" (roles outer/inner), ya que Overpass entrega los bordes
     * fragmentados en varios segmentos que hay que unir con LineMerger.
     */
    private MultiPolygon extraerMultiPolygon(String overpassJson, String nombreComuna) throws Exception {
        JsonNode root = objectMapper.readTree(overpassJson);
        JsonNode elements = root.path("elements");

        JsonNode relation = null;
        for (JsonNode el : elements) {
            if ("relation".equals(el.path("type").asText())) {
                relation = el;
                break;
            }
        }
        if (relation == null) {
            return null;
        }

        List<LineString> outerLines = new ArrayList<>();
        List<LineString> innerLines = new ArrayList<>();

        for (JsonNode member : relation.path("members")) {
            if (!"way".equals(member.path("type").asText())) {
                continue;
            }
            JsonNode geomArray = member.path("geometry");
            if (!geomArray.isArray() || geomArray.size() < 2) {
                continue;
            }
            Coordinate[] coords = new Coordinate[geomArray.size()];
            for (int i = 0; i < geomArray.size(); i++) {
                JsonNode pt = geomArray.get(i);
                coords[i] = new Coordinate(pt.path("lon").asDouble(), pt.path("lat").asDouble());
            }
            LineString line = geometryFactory.createLineString(coords);

            String role = member.path("role").asText("outer");
            if ("inner".equals(role)) {
                innerLines.add(line);
            } else {
                outerLines.add(line);
            }
        }

        if (outerLines.isEmpty()) {
            return null;
        }

        List<LinearRing> shells = fusionarEnAnillos(outerLines, nombreComuna, "outer");
        List<LinearRing> holes = fusionarEnAnillos(innerLines, nombreComuna, "inner");

        if (shells.isEmpty()) {
            return null;
        }

        List<Polygon> poligonos = new ArrayList<>();
        for (LinearRing shell : shells) {
            Polygon shellPoly = geometryFactory.createPolygon(shell);
            List<LinearRing> holesDeEsteShell = new ArrayList<>();
            for (LinearRing hole : holes) {
                Point holePoint = geometryFactory.createPoint(hole.getCoordinateN(0));
                if (shellPoly.contains(holePoint)) {
                    holesDeEsteShell.add(hole);
                }
            }
            poligonos.add(geometryFactory.createPolygon(shell, holesDeEsteShell.toArray(new LinearRing[0])));
        }

        Geometry union = geometryFactory.createMultiPolygon(poligonos.toArray(new Polygon[0]));
        if (!union.isValid()) {
            log.warn("Geometría inválida para '{}', aplicando buffer(0) para reparar", nombreComuna);
            union = union.buffer(0);
        }

        if (union instanceof MultiPolygon mp) {
            return mp;
        }
        if (union instanceof Polygon p) {
            return geometryFactory.createMultiPolygon(new Polygon[]{p});
        }
        log.error("Geometría reparada de '{}' no es un polígono válido (tipo: {})", nombreComuna, union.getGeometryType());
        return null;
    }

    private List<LinearRing> fusionarEnAnillos(List<LineString> segmentos, String nombreComuna, String rol) {
        List<LinearRing> anillos = new ArrayList<>();
        if (segmentos.isEmpty()) {
            return anillos;
        }

        LineMerger merger = new LineMerger();
        merger.add(segmentos);

        for (Object obj : merger.getMergedLineStrings()) {
            LineString merged = (LineString) obj;
            if (!merged.isClosed()) {
                log.warn("Anillo '{}' de la comuna '{}' no cerró correctamente (posible dato incompleto de OSM), se omite",
                        rol, nombreComuna);
                continue;
            }
            anillos.add(geometryFactory.createLinearRing(merged.getCoordinates()));
        }
        return anillos;
    }

    private void guardarComuna(String nombre, String distrito, MultiPolygon geom) {
        WKTWriter writer = new WKTWriter();
        String wkt = writer.write(geom);

        String sql = """
                INSERT INTO comuna_entidad (nombre, distrito_postal, geom)
                VALUES (?, ?, ST_Multi(ST_SetSRID(ST_GeomFromText(?), 4326)))
                ON CONFLICT (nombre) DO UPDATE
                    SET distrito_postal = EXCLUDED.distrito_postal,
                        geom = EXCLUDED.geom
                """;
        jdbc.update(sql, nombre, distrito, wkt);
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
