package com.motorbike.friend;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.auth.AppUserDetails;

@Controller
@RequestMapping("/friends")
public class FriendController {

    private final FriendshipService service;

    public FriendController(FriendshipService service) {
        this.service = service;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("overview", service.overview(me.getId()));
        return "friends/index";
    }

    @GetMapping("/search")
    public String search(@AuthenticationPrincipal AppUserDetails me,
                         @RequestParam(name = "q", required = false) String q, Model model) {
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("results", service.search(me.getId(), q));
        model.addAttribute("minLength", FriendshipService.MIN_QUERY_LENGTH);
        return "friends/search";
    }

    @PostMapping("/{username}/request")
    public String request(@AuthenticationPrincipal AppUserDetails me, @PathVariable String username,
                          @RequestParam(required = false) String back, RedirectAttributes redirect) {
        return act(redirect, back, () -> service.sendRequest(me.getId(), username) == Relation.FRIENDS
            ? "Anche " + username + " ti aveva inviato una richiesta: ora siete amici!"
            : "Richiesta di amicizia inviata a " + username + ".");
    }

    @PostMapping("/{username}/accept")
    public String accept(@AuthenticationPrincipal AppUserDetails me, @PathVariable String username,
                         @RequestParam(required = false) String back, RedirectAttributes redirect) {
        return act(redirect, back, () -> {
            service.accept(me.getId(), username);
            return "Tu e " + username + " ora siete amici.";
        });
    }

    @PostMapping("/{username}/decline")
    public String decline(@AuthenticationPrincipal AppUserDetails me, @PathVariable String username,
                          @RequestParam(required = false) String back, RedirectAttributes redirect) {
        return act(redirect, back, () -> {
            service.decline(me.getId(), username);
            return "Richiesta di " + username + " rifiutata.";
        });
    }

    @PostMapping("/{username}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserDetails me, @PathVariable String username,
                         @RequestParam(required = false) String back, RedirectAttributes redirect) {
        return act(redirect, back, () -> {
            service.cancel(me.getId(), username);
            return "Richiesta a " + username + " annullata.";
        });
    }

    @PostMapping("/{username}/remove")
    public String remove(@AuthenticationPrincipal AppUserDetails me, @PathVariable String username,
                         @RequestParam(required = false) String back, RedirectAttributes redirect) {
        return act(redirect, back, () -> {
            service.remove(me.getId(), username);
            return username + " è stato rimosso dai tuoi amici.";
        });
    }

    private String act(RedirectAttributes redirect, String back, java.util.function.Supplier<String> action) {
        try {
            redirect.addFlashAttribute("success", action.get());
        } catch (FriendshipException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + safeBack(back);
    }

    /** Accetta solo percorsi interni all'applicazione, per evitare open redirect. */
    static String safeBack(String back) {
        if (back == null || !back.startsWith("/") || back.startsWith("//") || back.contains("\\")
            || back.contains("\r") || back.contains("\n")) {
            return "/friends";
        }
        return back;
    }
}
