package com.motorbike.ai;

import java.util.List;
import java.util.Map;

/**
 * Sceglie tra luoghi reali (già trovati su Google Places) i più adatti a una tappa e ne spiega il motivo.
 * Non può aggiungere luoghi: restituisce solo id presenti tra i candidati.
 */
public interface TravelCurator {

    boolean isConfigured();

    Curation curate(StageContext stage, Map<PlaceCategory, List<Candidate>> candidates);

    /** Dati della tappa passati al modello. */
    record StageContext(String title, String date, String startName, String endName, Double km, String duration,
                        boolean avoidMotorways, List<String> stops, String notes, String highlights,
                        String preferences) {
    }

    /** Luogo candidato con la distanza dal punto di riferimento (arrivo o metà percorso). */
    record Candidate(Place place, double distanceKm, String near) {
    }

    record Pick(String placeId, String reason) {
    }

    record Curation(String summary, Map<PlaceCategory, List<Pick>> picks) {
    }

    class CurationException extends RuntimeException {
        public CurationException(String message) {
            super(message);
        }

        public CurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
