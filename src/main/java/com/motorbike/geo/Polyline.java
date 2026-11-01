package com.motorbike.geo;

import java.util.ArrayList;
import java.util.List;

/** Codifica/decodifica "encoded polyline" (algoritmo Google) con precisione 6, il formato restituito da Valhalla. */
public final class Polyline {

    private static final double FACTOR = 1e6;

    private Polyline() {
    }

    public static List<LatLon> decode(String encoded) {
        List<LatLon> points = new ArrayList<>();
        int index = 0;
        long lat = 0;
        long lon = 0;
        while (index < encoded.length()) {
            long[] r = next(encoded, index);
            lat += r[0];
            index = (int) r[1];
            r = next(encoded, index);
            lon += r[0];
            index = (int) r[1];
            points.add(new LatLon(lat / FACTOR, lon / FACTOR));
        }
        return points;
    }

    public static String encode(List<LatLon> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0;
        long prevLon = 0;
        for (LatLon p : points) {
            long lat = Math.round(p.lat() * FACTOR);
            long lon = Math.round(p.lon() * FACTOR);
            write(sb, lat - prevLat);
            write(sb, lon - prevLon);
            prevLat = lat;
            prevLon = lon;
        }
        return sb.toString();
    }

    private static long[] next(String s, int index) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            b = s.charAt(index++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[] {value, index};
    }

    private static void write(StringBuilder sb, long value) {
        long v = value < 0 ? ~(value << 1) : (value << 1);
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }
}
