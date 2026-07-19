package com.ecommerceb2b.backend.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy de geocodificación contra Nominatim (OSM) — mismo ecosistema que
 * ComunaOverpassLoader, mismo HttpClient de java.net.http.
 *
 * Vive en Services (no Loader) porque este es un proxy de request en vivo
 * disparado por el admin al escribir en el buscador, no un job puntual de
 * carga masiva.
 */
@Service
public class GeocodificacionServicio {

    private static final Logger log = LoggerFactory.getLogger(GeocodificacionServicio.class);

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "B2B-Backend/1.0 (contacto@tuempresa.cl)";
    private static final long MIN_INTERVALO_ENTRE_REQUESTS_MS = 1000; // política de uso de Nominatim: 1 req/s

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Object rateLimitLock = new Object();
    private long ultimoRequestMillis = 0;

    public GeocodificacionServicio() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Busca direcciones/lugares en Chile vía Nominatim y devuelve solo los
     * campos que necesita el frontend (displayName/lat/lon), no la respuesta
     * cruda completa.
     */
    public List<Map<String, Object>> buscar(String texto) throws Exception {
        respetarRateLimit();

        String query = "?q=" + URLEncoder.encode(texto, StandardCharsets.UTF_8)
                + "&format=json&countrycodes=cl&limit=5";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NOMINATIM_URL + query))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Nominatim respondió " + response.statusCode() + " para consulta '" + texto + "'");
        }

        JsonNode elementos = objectMapper.readTree(response.body());
        List<Map<String, Object>> resultados = new ArrayList<>();
        for (JsonNode el : elementos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("displayName", el.path("display_name").asText(null));
            item.put("lat", el.path("lat").asDouble());
            item.put("lon", el.path("lon").asDouble());
            resultados.add(item);
        }
        return resultados;
    }

    /**
     * Serializa todos los requests salientes a Nominatim a lo sumo a 1/s,
     * durmiendo el hilo (con el lock tomado) lo que falte desde el último
     * request — evita que llamadas concurrentes disparen ráfagas en paralelo.
     */
    private void respetarRateLimit() {
        synchronized (rateLimitLock) {
            long ahora = System.currentTimeMillis();
            long transcurrido = ahora - ultimoRequestMillis;
            long esperaNecesaria = MIN_INTERVALO_ENTRE_REQUESTS_MS - transcurrido;
            if (esperaNecesaria > 0) {
                try {
                    Thread.sleep(esperaNecesaria);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrumpido mientras se esperaba el rate limit de Nominatim");
                }
            }
            ultimoRequestMillis = System.currentTimeMillis();
        }
    }
}
