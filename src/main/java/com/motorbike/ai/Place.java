package com.motorbike.ai;

import java.util.List;

/**
 * Luogo reale da Google Places. Valutazioni e recensioni si mostrano solo da qui, mai generate dal modello.
 *
 * @param priceLevel da 1 (economico) a 4 (molto caro), null se sconosciuto
 */
public record Place(String id, String name, String typeLabel, String address, double lat, double lon,
                    Double rating, Integer ratingCount, Integer priceLevel, String summary, String mapsUrl,
                    String website, List<Review> reviews) {

    public record Review(String author, String authorUrl, Integer rating, String relativeTime, String text) {

        /** Estratto per la pagina e per il prompt. */
        public String excerpt(int max) {
            if (text == null) {
                return "";
            }
            String t = text.replaceAll("\\s+", " ").strip();
            return t.length() <= max ? t : t.substring(0, max - 1).strip() + "…";
        }
    }

    /** "€€" per la pagina. */
    public String priceSymbols() {
        return priceLevel == null || priceLevel < 1 ? null : "€".repeat(priceLevel);
    }

    /** Ordinamento senza AI: valutazione pesata sul numero di recensioni (un 5,0 con 3 recensioni non vince). */
    double score() {
        if (rating == null) {
            return 0;
        }
        int n = ratingCount == null ? 0 : ratingCount;
        return rating * Math.log10(n + 10);
    }
}
