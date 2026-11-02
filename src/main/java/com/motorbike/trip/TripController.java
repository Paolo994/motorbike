package com.motorbike.trip;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.ai.StageSuggestionService;
import com.motorbike.auth.AppUserDetails;
import com.motorbike.trip.TripEnums.AccommodationType;
import com.motorbike.trip.TripEnums.StopType;
import com.motorbike.trip.TripEnums.TripRole;
import com.motorbike.trip.TripExceptions.InvalidOperation;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/trips")
public class TripController {

    private final TripService service;
    private final TripMapJson mapJson;
    private final StageSuggestionService suggestions;

    public TripController(TripService service, TripMapJson mapJson, StageSuggestionService suggestions) {
        this.service = service;
        this.mapJson = mapJson;
        this.suggestions = suggestions;
    }

    // ================================================================ viaggi

    @GetMapping
    public String index(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("trips", service.myTrips(me.getId()));
        return "trips/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new TripForm());
        model.addAttribute("editing", false);
        return "trips/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails me, @Valid @ModelAttribute("form") TripForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            return "trips/form";
        }
        long id = service.create(me.getId(), form);
        redirect.addFlashAttribute("success", "Viaggio creato! Ora aggiungi la prima tappa.");
        return "redirect:/trips/" + id;
    }

    @GetMapping("/{id}")
    public String view(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, Model model) {
        var trip = service.view(me.getId(), id);
        model.addAttribute("trip", trip);
        model.addAttribute("mapJson", mapJson.of(id, trip.stages(), null, java.util.List.of()));
        if (trip.isOwner()) {
            model.addAttribute("invitable", service.invitableFriends(me.getId(), id));
        }
        model.addAttribute("roles", TripRole.values());
        return "trips/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, Model model) {
        model.addAttribute("form", service.editForm(me.getId(), id));
        model.addAttribute("editing", true);
        model.addAttribute("tripId", id);
        return "trips/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                         @Valid @ModelAttribute("form") TripForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("tripId", id);
            return "trips/form";
        }
        service.update(me.getId(), id, form);
        redirect.addFlashAttribute("success", "Viaggio aggiornato.");
        return "redirect:/trips/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, RedirectAttributes redirect) {
        service.delete(me.getId(), id);
        redirect.addFlashAttribute("success", "Viaggio eliminato.");
        return "redirect:/trips";
    }

    // ================================================================ partecipanti

    @PostMapping("/{id}/members")
    public String invite(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                         @RequestParam String username, @RequestParam TripRole role, RedirectAttributes redirect) {
        return act(redirect, "/trips/" + id, () -> {
            service.invite(me.getId(), id, username, role);
            return username + " è stato aggiunto al viaggio.";
        });
    }

    @PostMapping("/{id}/members/{userId}/role")
    public String changeRole(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                             @PathVariable Long userId, @RequestParam TripRole role, RedirectAttributes redirect) {
        return act(redirect, "/trips/" + id, () -> {
            service.changeRole(me.getId(), id, userId, role);
            return "Ruolo aggiornato.";
        });
    }

    @PostMapping("/{id}/members/{userId}/remove")
    public String removeMember(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                               @PathVariable Long userId, RedirectAttributes redirect) {
        return act(redirect, "/trips/" + id, () -> {
            service.removeMember(me.getId(), id, userId);
            return "Partecipante rimosso.";
        });
    }

    @PostMapping("/{id}/leave")
    public String leave(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.leave(me.getId(), id);
            redirect.addFlashAttribute("success", "Hai lasciato il viaggio.");
            return "redirect:/trips";
        } catch (InvalidOperation e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/trips/" + id;
        }
    }

    // ================================================================ tappe

    @GetMapping("/{id}/stages/new")
    public String newStage(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, Model model) {
        model.addAttribute("form", service.newStageForm(me.getId(), id));
        return stageFormView(me, id, null, model);
    }

    @PostMapping("/{id}/stages")
    public String createStage(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                              @Valid @ModelAttribute("form") StageForm form, BindingResult binding, Model model,
                              RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return stageFormView(me, id, null, model);
        }
        long stageId = service.saveStage(me.getId(), id, null, form);
        redirect.addFlashAttribute("success", "Tappa aggiunta.");
        return "redirect:/trips/" + id + "/stages/" + stageId;
    }

    @GetMapping("/{id}/stages/{stageId}")
    public String stage(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, @PathVariable Long stageId,
                        Model model) {
        var detail = service.stageDetail(me.getId(), id, stageId);
        model.addAttribute("detail", detail);
        model.addAttribute("aiStatus", suggestions.status().name());
        model.addAttribute("suggestions", suggestions.cached(me.getId(), id, stageId).orElse(null));
        model.addAttribute("mapJson", mapJson.of(id, java.util.List.of(detail.stage()), stageId,
            detail.cheapestNearStart()));
        return "trips/stage";
    }

    @GetMapping("/{id}/stages/{stageId}/edit")
    public String editStage(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                            @PathVariable Long stageId, Model model) {
        model.addAttribute("form", service.stageForm(me.getId(), id, stageId));
        return stageFormView(me, id, stageId, model);
    }

    @PostMapping("/{id}/stages/{stageId}")
    public String updateStage(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                              @PathVariable Long stageId, @Valid @ModelAttribute("form") StageForm form,
                              BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return stageFormView(me, id, stageId, model);
        }
        service.saveStage(me.getId(), id, stageId, form);
        redirect.addFlashAttribute("success", "Tappa aggiornata.");
        return "redirect:/trips/" + id + "/stages/" + stageId;
    }

    @PostMapping("/{id}/stages/{stageId}/delete")
    public String deleteStage(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                              @PathVariable Long stageId, RedirectAttributes redirect) {
        service.deleteStage(me.getId(), id, stageId);
        redirect.addFlashAttribute("success", "Tappa eliminata.");
        return "redirect:/trips/" + id;
    }

    @PostMapping("/{id}/stages/{stageId}/move")
    public String moveStage(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                            @PathVariable Long stageId, @RequestParam String dir) {
        service.moveStage(me.getId(), id, stageId, "up".equals(dir));
        return "redirect:/trips/" + id + "#tappe";
    }

    @PostMapping("/{id}/stages/{stageId}/reroute")
    public String reroute(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                          @PathVariable Long stageId, RedirectAttributes redirect) {
        service.reroute(me.getId(), id, stageId);
        redirect.addFlashAttribute("success", "Percorso ricalcolato.");
        return "redirect:/trips/" + id + "/stages/" + stageId;
    }

    // ================================================================ helper

    private String stageFormView(AppUserDetails me, Long tripId, Long stageId, Model model) {
        model.addAttribute("trip", service.view(me.getId(), tripId));
        model.addAttribute("stageId", stageId);
        model.addAttribute("stopTypes", StopType.values());
        model.addAttribute("accommodationTypes", AccommodationType.values());
        model.addAttribute("maxStops", StageForm.MAX_STOPS);
        model.addAttribute("aiStatus", suggestions.status().name());
        return "trips/stage-form";
    }

    private String act(RedirectAttributes redirect, String target, java.util.function.Supplier<String> action) {
        try {
            redirect.addFlashAttribute("success", action.get());
        } catch (InvalidOperation e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + target;
    }
}
