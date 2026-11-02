package com.motorbike.trip;

import java.time.Instant;
import java.util.List;

import com.motorbike.geo.LatLon;

/** Dati di una tappa usati dal modulo dei consigli (ai). updatedAt invalida i consigli in cache. */
public record StageAiContext(long tripId, long stageId, Instant updatedAt, String title, String date,
                             String startName, String endName, LatLon start, LatLon end, LatLon halfway,
                             Double km, String duration, boolean avoidMotorways, List<String> stops,
                             String notes, String highlights) {
}
