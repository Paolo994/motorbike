package com.motorbike.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.motorbike.geo.LatLon;

class GoogleMapsLinksTest {

    @Test
    void buildsDirectionsUrlWithWaypoints() {
        var link = GoogleMapsLinks.directions(List.of(
            new LatLon(43.7696, 11.2558), new LatLon(43.47, 11.36), new LatLon(43.3188, 11.3308)));
        assertThat(link.url())
            .startsWith("https://www.google.com/maps/dir/?api=1")
            .contains("&origin=43.769600,11.255800")
            .contains("&destination=43.318800,11.330800")
            .contains("&travelmode=driving");
        assertThat(URLDecoder.decode(link.url(), StandardCharsets.UTF_8)).contains("&waypoints=43.470000,11.360000");
        assertThat(link.waypoints()).isEqualTo(1);
        assertThat(link.dropped()).isZero();
        assertThat(link.exceedsMobileLimit()).isFalse();
    }

    @Test
    void withoutStopsThereAreNoWaypoints() {
        var link = GoogleMapsLinks.directions(List.of(new LatLon(45, 9), new LatLon(46, 10)));
        assertThat(link.url()).doesNotContain("waypoints");
    }

    @Test
    void keepsAtMostNineWaypoints() {
        List<LatLon> points = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            points.add(new LatLon(44 + i * 0.1, 10));
        }
        var link = GoogleMapsLinks.directions(points);               // 11 punti intermedi
        assertThat(link.waypoints()).isEqualTo(11);
        assertThat(link.dropped()).isEqualTo(2);
        assertThat(link.exceedsMobileLimit()).isTrue();
        String waypoints = URLDecoder.decode(link.url(), StandardCharsets.UTF_8).replaceAll(".*&waypoints=", "");
        assertThat(waypoints.split("\\|")).hasSize(9);
        assertThat(link.url()).contains("&destination=45.200000,10.000000");   // l'arrivo resta quello vero
    }

    @Test
    void picksIconsFromValhallaInstructions() {
        assertThat(DirectionStep.iconFor("Svolta a destra su Viale della Giovine Italia.")).isEqualTo("bi-arrow-90deg-right");
        assertThat(DirectionStep.iconFor("Svolta leggermente a sinistra su SP2.")).isEqualTo("bi-arrow-up-left");
        assertThat(DirectionStep.iconFor("Entra nella rotatoria e prendi la 2ª uscita.")).isEqualTo("bi-arrow-repeat");
        assertThat(DirectionStep.iconFor("Mantieni la destra per imboccare SR222.")).isEqualTo("bi-arrow-up-right");
        assertThat(DirectionStep.iconFor("Sei arrivato a destinazione.")).isEqualTo("bi-flag-fill");
        assertThat(DirectionStep.iconFor("Guida verso est su Via Ghibellina.")).isEqualTo("bi-geo-alt-fill");
        assertThat(DirectionStep.iconFor("Prosegui su SS2.")).isEqualTo("bi-arrow-up");
    }
}
