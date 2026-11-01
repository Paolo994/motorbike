package com.motorbike.geo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.motorbike.config.AppProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ricerca di luoghi con Nominatim (OpenStreetMap). Rispetta la policy d'uso del servizio pubblico:
 * User-Agent identificativo, al massimo 1 richiesta al secondo, risultati in cache.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final long MIN_INTERVAL_MS = 1100;
    private static final int CACHE_SIZE = 500;

    public record Place(String name, String label, double lat, double lon) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;
    private final String baseUrl;
    private final String userAgent;
    private long lastCall;

    private final Map<String, List<Place>> cache = Collections.synchronizedMap(
        new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Place>> eldest) {
                return size() > CACHE_SIZE;
            }
        });

    public GeocodingService(JsonMapper json, AppProperties props) {
        this.json = json;
        this.baseUrl = props.geocoding().nominatimUrl().replaceAll("/+$", "");
        this.userAgent = props.http().userAgent();
    }

    public List<Place> search(String query) {
        String q = query == null ? "" : query.strip();
        if (q.length() < 3 || q.length() > 200) {
            return List.of();
        }
        String key = q.toLowerCase(Locale.ROOT);
        List<Place> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            List<Place> result = call(q);
            cache.put(key, result);
            return result;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Geocoding non disponibile: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Place> call(String q) throws Exception {
        throttle();
        String url = baseUrl + "/search?format=jsonv2&limit=6&addressdetails=0&accept-language=it&q="
            + URLEncoder.encode(q, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", userAgent)
            .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Nominatim HTTP " + response.statusCode());
        }
        List<Place> places = new ArrayList<>();
        for (JsonNode n : json.readTree(response.body())) {
            String label = n.path("display_name").asString("");
            String name = n.path("name").asString("");
            places.add(new Place(name.isBlank() ? label.split(",")[0] : name, label,
                Double.parseDouble(n.path("lat").asString("0")), Double.parseDouble(n.path("lon").asString("0"))));
        }
        return places;
    }

    /** Massimo una richiesta al secondo verso il servizio pubblico. */
    private synchronized void throttle() throws InterruptedException {
        long wait = lastCall + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastCall = System.currentTimeMillis();
    }
}
