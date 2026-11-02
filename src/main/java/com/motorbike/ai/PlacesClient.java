package com.motorbike.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.motorbike.config.AppProperties;
import com.motorbike.geo.LatLon;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Client di Google Places API (New), endpoint Nearby Search.
 * Nota sulle condizioni d'uso di Google: i dati dei luoghi non vanno mostrati su mappe non Google
 * (qui usiamo OpenStreetMap), né salvati oltre il necessario: li teniamo solo in una cache in memoria.
 */
@Component
public class PlacesClient {

    /** Campi richiesti: determinano anche il costo della chiamata. */
    static final String FIELD_MASK = String.join(",",
        "places.id", "places.displayName", "places.primaryTypeDisplayName", "places.formattedAddress",
        "places.location", "places.rating", "places.userRatingCount", "places.priceLevel",
        "places.editorialSummary", "places.googleMapsUri", "places.websiteUri", "places.reviews");

    public static class PlacesUnavailableException extends RuntimeException {
        public PlacesUnavailableException(String message) {
            super(message);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;
    private final AppProperties.Places config;

    public PlacesClient(JsonMapper json, AppProperties props) {
        this.json = json;
        this.config = props.places();
    }

    public boolean isConfigured() {
        return config.configured();
    }

    /** Luoghi della categoria entro {@code radiusMeters} da {@code center}, ordinati per popolarità. */
    public List<Place> searchNearby(PlaceCategory category, LatLon center, int radiusMeters, int max) {
        if (!isConfigured()) {
            throw new PlacesUnavailableException("Chiave Google Places non configurata");
        }
        ObjectNode body = json.createObjectNode();
        var types = body.putArray("includedTypes");
        category.placeTypes().forEach(types::add);
        body.put("maxResultCount", Math.min(20, max));
        body.put("rankPreference", "POPULARITY");
        body.put("languageCode", "it");
        ObjectNode circle = body.putObject("locationRestriction").putObject("circle");
        circle.putObject("center").put("latitude", center.lat()).put("longitude", center.lon());
        circle.put("radius", (double) Math.min(50_000, radiusMeters));

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/v1/places:searchNearby"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Key", config.apiKey())
            .header("X-Goog-FieldMask", FIELD_MASK)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // il corpo dell'errore non contiene la chiave, ma lo tronchiamo comunque
                String detail = response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length()));
                throw new PlacesUnavailableException("Google Places HTTP " + response.statusCode() + ": " + detail);
            }
            return parse(json.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PlacesUnavailableException("Richiesta interrotta");
        } catch (java.io.IOException e) {
            throw new PlacesUnavailableException("Google Places non raggiungibile: " + e.getMessage());
        }
    }

    static List<Place> parse(JsonNode root) {
        List<Place> places = new ArrayList<>();
        for (JsonNode p : root.path("places")) {
            String id = p.path("id").asString("");
            String name = p.path("displayName").path("text").asString("");
            if (id.isEmpty() || name.isEmpty() || p.path("location").isMissingNode()) {
                continue;
            }
            List<Place.Review> reviews = new ArrayList<>();
            for (JsonNode r : p.path("reviews")) {
                String text = r.path("text").path("text").asString(r.path("originalText").path("text").asString(""));
                if (!text.isBlank()) {
                    reviews.add(new Place.Review(
                        r.path("authorAttribution").path("displayName").asString("Utente Google"),
                        httpOrNull(r.path("authorAttribution").path("uri").asString(null)),
                        r.has("rating") ? r.path("rating").asInt() : null,
                        r.path("relativePublishTimeDescription").asString(null),
                        text));
                }
            }
            places.add(new Place(id, name,
                p.path("primaryTypeDisplayName").path("text").asString(null),
                p.path("formattedAddress").asString(null),
                p.path("location").path("latitude").asDouble(),
                p.path("location").path("longitude").asDouble(),
                p.has("rating") ? p.path("rating").asDouble() : null,
                p.has("userRatingCount") ? p.path("userRatingCount").asInt() : null,
                priceLevel(p.path("priceLevel").asString("")),
                p.path("editorialSummary").path("text").asString(null),
                httpOrNull(p.path("googleMapsUri").asString(null)),
                httpOrNull(p.path("websiteUri").asString(null)),
                reviews));
        }
        return places;
    }

    private static Integer priceLevel(String value) {
        return switch (value) {
            case "PRICE_LEVEL_FREE" -> 0;
            case "PRICE_LEVEL_INEXPENSIVE" -> 1;
            case "PRICE_LEVEL_MODERATE" -> 2;
            case "PRICE_LEVEL_EXPENSIVE" -> 3;
            case "PRICE_LEVEL_VERY_EXPENSIVE" -> 4;
            default -> null;
        };
    }

    /** Solo link http(s): finiscono negli href della pagina. */
    private static String httpOrNull(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://")) ? url : null;
    }
}
