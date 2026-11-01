package com.motorbike.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.motorbike.config.AppProperties;

import tools.jackson.databind.json.JsonMapper;

class GeoTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void polylineRoundTripWithPrecision6() {
        List<LatLon> points = List.of(new LatLon(43.7696, 11.2558), new LatLon(43.770123, 11.256789),
            new LatLon(46.528617, 10.453205), new LatLon(-33.9, 151.2));
        List<LatLon> decoded = Polyline.decode(Polyline.encode(points));
        assertThat(decoded).hasSize(points.size());
        for (int i = 0; i < points.size(); i++) {
            assertThat(decoded.get(i).lat()).isCloseTo(points.get(i).lat(), within(1e-6));
            assertThat(decoded.get(i).lon()).isCloseTo(points.get(i).lon(), within(1e-6));
        }
    }

    @Test
    void haversineDistance() {
        // Firenze - Siena in linea d'aria: circa 50 km
        assertThat(new LatLon(43.7696, 11.2558).distanceKm(new LatLon(43.3188, 11.3308))).isCloseTo(50.5, within(1.0));
    }

    /** Risposta Valhalla con due tratte (partenza → fermata → arrivo): geometrie unite, manovre in ordine. */
    @Test
    void parsesValhallaResponse() {
        String leg1 = Polyline.encode(List.of(new LatLon(43.0, 11.0), new LatLon(43.1, 11.1)));
        String leg2 = Polyline.encode(List.of(new LatLon(43.1, 11.1), new LatLon(43.2, 11.2)));
        String body = """
            {"trip": {
               "summary": {"length": 76.856, "time": 7811.4, "has_highway": false, "has_toll": false},
               "legs": [
                 {"shape": "%s", "maneuvers": [
                    {"instruction": "Guida verso est su Via Ghibellina.", "length": 0.981},
                    {"instruction": "Sei arrivato alla tua fermata.", "length": 0.0}]},
                 {"shape": "%s", "maneuvers": [
                    {"instruction": "Svolta a destra su SR222.", "length": 12.34}]}
               ]}}
            """.formatted(leg1.replace("\\", "\\\\"), leg2.replace("\\", "\\\\"));
        RoutingService service = new RoutingService(json, props());
        RouteResult r = service.parse(json.readTree(body));

        assertThat(r.distanceMeters()).isEqualTo(76856);
        assertThat(r.durationSeconds()).isEqualTo(7811);
        assertThat(r.hasMotorway()).isFalse();
        assertThat(r.estimated()).isFalse();
        assertThat(Polyline.decode(r.shape())).hasSize(3);                // punto di giunzione non duplicato
        assertThat(r.maneuvers()).extracting(RouteResult.Maneuver::text)
            .containsExactly("Guida verso est su Via Ghibellina.", "Sei arrivato alla tua fermata.", "Svolta a destra su SR222.");
        assertThat(r.maneuvers().get(2).km()).isEqualTo(12.3);
    }

    @Test
    void fallsBackToStraightLineEstimateWhenServiceIsDown() {
        // URL non raggiungibile: nessuna eccezione, stima in linea d'aria × 1,3 a 60 km/h
        RoutingService service = new RoutingService(json, new AppProperties(null, null, null, null,
            new AppProperties.Http("test"), null, null, new AppProperties.Routing("http://127.0.0.1:9"), null));
        RouteResult r = service.route(List.of(new LatLon(43.7696, 11.2558), new LatLon(43.3188, 11.3308)), true, false);
        assertThat(r.estimated()).isTrue();
        assertThat(r.distanceMeters() / 1000.0).isCloseTo(50.5 * 1.3, within(2.0));
        assertThat(r.durationSeconds()).isCloseTo((int) (r.distanceMeters() / 1000.0 / 60 * 3600), within(2));
        assertThat(r.maneuvers()).isEmpty();
    }

    private static AppProperties props() {
        return new AppProperties(null, null, null, null, new AppProperties.Http("test"), null, null,
            new AppProperties.Routing("http://localhost"), null);
    }
}
