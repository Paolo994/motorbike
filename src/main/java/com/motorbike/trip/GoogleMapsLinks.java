package com.motorbike.trip;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.motorbike.geo.LatLon;

/**
 * Link "Apri in Google Maps" (Maps URLs, https://developers.google.com/maps/documentation/urls).
 * Il link apre l'app Google Maps sul telefono, o il sito sul computer, con il percorso già impostato.
 */
public final class GoogleMapsLinks {

    /** Tappe intermedie accettate da Google Maps su computer; sui browser mobili il limite è 3. */
    public static final int MAX_WAYPOINTS = 9;
    public static final int MAX_WAYPOINTS_MOBILE = 3;

    private GoogleMapsLinks() {
    }

    /** Link di navigazione. Oltre {@link #MAX_WAYPOINTS} punti intermedi tiene i primi 9. */
    public static Link directions(List<LatLon> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("Servono almeno partenza e arrivo");
        }
        List<LatLon> middle = points.subList(1, points.size() - 1);
        List<LatLon> kept = middle.subList(0, Math.min(MAX_WAYPOINTS, middle.size()));
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1")
            .append("&origin=").append(coord(points.getFirst()))
            .append("&destination=").append(coord(points.getLast()))
            .append("&travelmode=driving");
        if (!kept.isEmpty()) {
            String waypoints = kept.stream().map(GoogleMapsLinks::coord).collect(Collectors.joining("|"));
            url.append("&waypoints=").append(URLEncoder.encode(waypoints, StandardCharsets.UTF_8));
        }
        return new Link(url.toString(), middle.size(), middle.size() - kept.size());
    }

    private static String coord(LatLon p) {
        return String.format(Locale.ROOT, "%.6f,%.6f", p.lat(), p.lon());
    }

    /**
     * @param waypoints numero di punti intermedi del percorso
     * @param dropped   punti intermedi esclusi dal link per il limite di Google Maps
     */
    public record Link(String url, int waypoints, int dropped) {

        /** Sui browser mobili Google Maps considera solo i primi 3 punti intermedi. */
        public boolean exceedsMobileLimit() {
            return waypoints - dropped > MAX_WAYPOINTS_MOBILE;
        }
    }
}
