package com.motorbike.ai;

import java.util.List;

/** Categorie di luoghi consigliati, con i tipi Google Places corrispondenti. */
public enum PlaceCategory {

    LODGING("Dove dormire", "bi-house-door", List.of("hostel", "bed_and_breakfast", "guest_house"), 3),
    ATTRACTION("Cosa vedere", "bi-camera", List.of("tourist_attraction", "museum", "historical_landmark", "national_park"), 4),
    RESTAURANT("Dove mangiare", "bi-cup-hot", List.of("restaurant"), 3);

    private final String label;
    private final String icon;
    private final List<String> placeTypes;
    private final int maxPicks;

    PlaceCategory(String label, String icon, List<String> placeTypes, int maxPicks) {
        this.label = label;
        this.icon = icon;
        this.placeTypes = placeTypes;
        this.maxPicks = maxPicks;
    }

    public String getLabel() { return label; }
    public String getIcon() { return icon; }
    public List<String> placeTypes() { return placeTypes; }
    public int maxPicks() { return maxPicks; }
}
