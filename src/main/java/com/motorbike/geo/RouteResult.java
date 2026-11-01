package com.motorbike.geo;

import java.util.List;

/**
 * Percorso calcolato.
 *
 * @param estimated true se il servizio di routing non era disponibile e distanza/tempo sono stimati in linea d'aria
 */
public record RouteResult(int distanceMeters, int durationSeconds, boolean hasMotorway, boolean hasToll,
                          String shape, List<Maneuver> maneuvers, boolean estimated) {

    /** Indicazione svolta per svolta. */
    public record Maneuver(String text, double km) {
    }
}
