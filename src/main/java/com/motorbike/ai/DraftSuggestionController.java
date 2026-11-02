package com.motorbike.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.motorbike.ai.StageSuggestionService.SuggestionException;
import com.motorbike.auth.AppUserDetails;
import com.motorbike.geo.LatLon;

/**
 * Suggerimenti per il form della tappa ("Cerca suggerimenti" accanto a "Cosa vedere" e "Alloggio").
 * Funziona anche su una tappa non ancora salvata: usa le coordinate presenti nel form.
 * Risponde JSON; il browser compila il form con il luogo scelto.
 */
@RestController
public class DraftSuggestionController {

    /** Luogo suggerito, nel formato usato da stage-form.js. */
    public record PlaceJson(String name, String typeLabel, String address, Double rating, Integer ratingCount,
                            String price, String mapsUrl, String website, String reason, double distanceKm,
                            String near, String review, String reviewAuthor) {
    }

    public record PreviewJson(boolean aiGenerated, List<PlaceJson> places) {
    }

    private final StageSuggestionService service;

    public DraftSuggestionController(StageSuggestionService service) {
        this.service = service;
    }

    /** category: ATTRACTION ("Cosa vedere") o LODGING ("Alloggio a fine tappa"). */
    @PostMapping("/trips/{tripId}/suggestions/preview")
    public PreviewJson preview(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long tripId,
                               @RequestParam PlaceCategory category,
                               @RequestParam(required = false) String title,
                               @RequestParam(required = false) String startName,
                               @RequestParam double startLat, @RequestParam double startLon,
                               @RequestParam(required = false) String endName,
                               @RequestParam double endLat, @RequestParam double endLon,
                               @RequestParam(required = false) List<Double> stopLat,
                               @RequestParam(required = false) List<Double> stopLon,
                               @RequestParam(defaultValue = "false") boolean avoidMotorways,
                               @RequestParam(required = false) String preferences) {
        if (category == PlaceCategory.RESTAURANT) {
            throw new SuggestionException("Categoria non prevista nel form.");
        }
        List<LatLon> stops = new ArrayList<>();
        if (stopLat != null && stopLon != null) {
            for (int i = 0; i < Math.min(stopLat.size(), stopLon.size()) && i < 15; i++) {
                stops.add(new LatLon(stopLat.get(i), stopLon.get(i)));
            }
        }
        var draft = new StageSuggestionService.Draft(title, blankTo(startName, "Partenza"), blankTo(endName, "Arrivo"),
            new LatLon(startLat, startLon), new LatLon(endLat, endLon), stops, avoidMotorways, preferences);
        var result = service.preview(me.getId(), tripId, draft, category);

        List<PlaceJson> places = result.sections().stream().flatMap(s -> s.items().stream()).map(i -> {
            Place p = i.place();
            Place.Review first = p.reviews().isEmpty() ? null : p.reviews().getFirst();
            return new PlaceJson(p.name(), p.typeLabel(), p.address(), p.rating(), p.ratingCount(), p.priceSymbols(),
                p.mapsUrl(), p.website(), i.reason(), i.distanceKm(), i.near(),
                first == null ? null : first.excerpt(200), first == null ? null : first.author());
        }).toList();
        return new PreviewJson(result.aiGenerated(), places);
    }

    @ExceptionHandler(SuggestionException.class)
    public ResponseEntity<Map<String, String>> error(SuggestionException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badCoordinates(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Coordinate non valide."));
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.strip();
    }
}
