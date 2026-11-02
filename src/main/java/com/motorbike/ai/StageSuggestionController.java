package com.motorbike.ai;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.ai.StageSuggestionService.SuggestionException;
import com.motorbike.auth.AppUserDetails;
import com.motorbike.trip.TripExceptions.InvalidOperation;

@Controller
@RequestMapping("/trips/{tripId}/stages/{stageId}/suggestions")
public class StageSuggestionController {

    private final StageSuggestionService service;

    public StageSuggestionController(StageSuggestionService service) {
        this.service = service;
    }

    /** category: ALL (default) oppure LODGING, ATTRACTION, RESTAURANT per una ricerca specifica. */
    @PostMapping
    public String generate(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long tripId,
                           @PathVariable Long stageId, @RequestParam(required = false) String preferences,
                           @RequestParam(defaultValue = "ALL") String category, RedirectAttributes redirect) {
        Set<PlaceCategory> categories = "ALL".equals(category)
            ? EnumSet.allOf(PlaceCategory.class) : EnumSet.of(PlaceCategory.valueOf(category));
        try {
            var result = service.generate(me.getId(), tripId, stageId, preferences, categories);
            if (!result.aiGenerated()) {
                redirect.addFlashAttribute("info",
                    "L'assistente AI non è disponibile: ti mostro i luoghi meglio valutati nei dintorni.");
            }
        } catch (SuggestionException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return back(tripId, stageId);
    }

    @PostMapping("/{placeId}/stop")
    public String addAsStop(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long tripId,
                            @PathVariable Long stageId, @PathVariable String placeId, RedirectAttributes redirect) {
        try {
            String name = service.addAsStop(me.getId(), tripId, stageId, placeId);
            redirect.addFlashAttribute("success", name + " aggiunto come fermata: percorso ricalcolato.");
        } catch (SuggestionException | InvalidOperation e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/trips/" + tripId + "/stages/" + stageId;
    }

    @PostMapping("/{placeId}/accommodation")
    public String useAsAccommodation(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long tripId,
                                     @PathVariable Long stageId, @PathVariable String placeId,
                                     RedirectAttributes redirect) {
        try {
            String name = service.useAsAccommodation(me.getId(), tripId, stageId, placeId);
            redirect.addFlashAttribute("success", name + " impostato come alloggio della tappa. Aggiungi il prezzo da \"Modifica\".");
        } catch (SuggestionException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/trips/" + tripId + "/stages/" + stageId;
    }

    private static String back(Long tripId, Long stageId) {
        return "redirect:/trips/" + tripId + "/stages/" + stageId + "#consigli";
    }
}
