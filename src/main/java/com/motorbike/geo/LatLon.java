package com.motorbike.geo;

public record LatLon(double lat, double lon) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    public LatLon {
        if (!(lat >= -90 && lat <= 90) || !(lon >= -180 && lon <= 180)) {
            throw new IllegalArgumentException("Coordinate non valide: " + lat + ", " + lon);
        }
    }

    /** Distanza in linea d'aria (formula dell'emisenoverso), in km. */
    public double distanceKm(LatLon other) {
        double dLat = Math.toRadians(other.lat - lat);
        double dLon = Math.toRadians(other.lon - lon);
        double a = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
