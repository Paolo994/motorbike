package com.motorbike.geo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.motorbike.config.AppProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calcolo percorsi con Valhalla, profilo "motorcycle": supporta l'esclusione di autostrade e pedaggi
 * e restituisce le indicazioni in italiano. Se il servizio non risponde, ripiega su una stima in linea d'aria.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    /** Fattore medio strada/linea d'aria e velocità media usati per la stima di ripiego. */
    static final double ROAD_FACTOR = 1.3;
    static final double FALLBACK_SPEED_KMH = 60;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;
    private final String baseUrl;
    private final String userAgent;

    public RoutingService(JsonMapper json, AppProperties props) {
        this.json = json;
        this.baseUrl = props.routing().valhallaUrl().replaceAll("/+$", "");
        this.userAgent = props.http().userAgent();
    }

    public RouteResult route(List<LatLon> points, boolean avoidMotorways, boolean avoidTolls) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("Servono almeno partenza e arrivo");
        }
        try {
            return callValhalla(points, avoidMotorways, avoidTolls);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Routing non disponibile ({}), uso la stima in linea d'aria", e.getMessage());
            return estimate(points);
        }
    }

    private RouteResult callValhalla(List<LatLon> points, boolean avoidMotorways, boolean avoidTolls) throws Exception {
        ObjectNode body = json.createObjectNode();
        ArrayNode locations = body.putArray("locations");
        for (LatLon p : points) {
            locations.addObject().put("lat", p.lat()).put("lon", p.lon()).put("type", "break");
        }
        body.put("costing", "motorcycle");
        ObjectNode moto = body.putObject("costing_options").putObject("motorcycle");
        // 0 = evitare il più possibile; 1 = nessuna penalità (default per autostrade e pedaggi)
        moto.put("use_highways", avoidMotorways ? 0.0 : 1.0);
        moto.put("use_tolls", avoidTolls || avoidMotorways ? 0.0 : 1.0);
        body.putObject("directions_options").put("language", "it-IT").put("units", "kilometers");

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/route"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Valhalla HTTP " + response.statusCode());
        }
        return parse(json.readTree(response.body()));
    }

    /** Converte la risposta Valhalla: unisce le geometrie e le manovre di tutte le tratte. */
    RouteResult parse(JsonNode root) {
        JsonNode trip = root.path("trip");
        JsonNode summary = trip.path("summary");
        List<LatLon> shape = new ArrayList<>();
        List<RouteResult.Maneuver> maneuvers = new ArrayList<>();
        for (JsonNode leg : trip.path("legs")) {
            List<LatLon> legShape = Polyline.decode(leg.path("shape").asString(""));
            // il primo punto di una tratta coincide con l'ultimo della precedente
            shape.addAll(shape.isEmpty() ? legShape : legShape.subList(Math.min(1, legShape.size()), legShape.size()));
            for (JsonNode m : leg.path("maneuvers")) {
                maneuvers.add(new RouteResult.Maneuver(m.path("instruction").asString(""),
                    Math.round(m.path("length").asDouble() * 10) / 10.0));
            }
        }
        if (shape.isEmpty()) {
            throw new IllegalStateException("Risposta di routing senza percorso");
        }
        return new RouteResult(
            (int) Math.round(summary.path("length").asDouble() * 1000),
            (int) Math.round(summary.path("time").asDouble()),
            summary.path("has_highway").asBoolean(false),
            summary.path("has_toll").asBoolean(false),
            Polyline.encode(shape), maneuvers, false);
    }

    /** Stima di ripiego: linea d'aria × 1,3 a 60 km/h, tracciato rettilineo tra i punti. */
    static RouteResult estimate(List<LatLon> points) {
        double km = 0;
        for (int i = 1; i < points.size(); i++) {
            km += points.get(i - 1).distanceKm(points.get(i));
        }
        km *= ROAD_FACTOR;
        return new RouteResult((int) Math.round(km * 1000), (int) Math.round(km / FALLBACK_SPEED_KMH * 3600),
            false, false, Polyline.encode(points), List.of(), true);
    }
}
