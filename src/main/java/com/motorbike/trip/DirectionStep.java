package com.motorbike.trip;

import java.util.Locale;

/** Indicazione stradale pronta per la pagina: numero, testo, distanza e icona della manovra. */
public record DirectionStep(int number, String text, double km, String icon) {

    /** Icona Bootstrap scelta dal testo dell'indicazione (Valhalla, in italiano). */
    static String iconFor(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("arrivat") || t.contains("destinazione")) {
            return "bi-flag-fill";
        }
        if (t.contains("rotatoria")) {
            return "bi-arrow-repeat";
        }
        if (t.contains("inversione")) {
            return "bi-arrow-return-left";
        }
        if (t.contains("uscita")) {
            return "bi-signpost-2";
        }
        if (t.contains("leggermente a destra") || t.contains("mantieni la destra")) {
            return "bi-arrow-up-right";
        }
        if (t.contains("leggermente a sinistra") || t.contains("mantieni la sinistra")) {
            return "bi-arrow-up-left";
        }
        if (t.contains("destra")) {
            return "bi-arrow-90deg-right";
        }
        if (t.contains("sinistra")) {
            return "bi-arrow-90deg-left";
        }
        if (t.contains("immetti") || t.contains("imbocca") || t.contains("entra")) {
            return "bi-sign-merge-right";
        }
        if (t.startsWith("guida verso") || t.startsWith("parti")) {
            return "bi-geo-alt-fill";
        }
        return "bi-arrow-up";
    }
}
