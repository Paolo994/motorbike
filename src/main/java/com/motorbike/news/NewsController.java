package com.motorbike.news;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.auth.AppUserDetails;

@Controller
@RequestMapping("/news")
public class NewsController {

    private final NewsService news;

    public NewsController(NewsService news) {
        this.news = news;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal AppUserDetails me,
                        @RequestParam(required = false) String topic,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        NewsTopic selected = news.topic(topic).orElse(null);
        model.addAttribute("topics", news.topics());
        model.addAttribute("selected", selected);
        model.addAttribute("articles", news.latest(selected, page));
        model.addAttribute("subscriptions", news.subscriptions(me.getId()));
        return "news/index";
    }

    /**
     * Gestione iscrizioni. action: follow | unfollow | email-on | email-off.
     * Seguire un topic attiva sempre le notifiche in-app; l'email è opzionale.
     */
    @PostMapping("/subscriptions")
    public String subscription(@AuthenticationPrincipal AppUserDetails me, @RequestParam String topic,
                               @RequestParam String action, @RequestParam(required = false) String back,
                               RedirectAttributes redirect) {
        String message = switch (action) {
            case "follow" -> {
                news.follow(me.getId(), topic, true);
                yield "Ora segui questo topic: riceverai notifiche ed email per i nuovi articoli.";
            }
            case "email-on" -> {
                news.follow(me.getId(), topic, true);
                yield "Email attivate per questo topic.";
            }
            case "email-off" -> {
                news.follow(me.getId(), topic, false);
                yield "Email disattivate: continuerai a ricevere le notifiche nell'app.";
            }
            case "unfollow" -> {
                news.unfollow(me.getId(), topic);
                yield "Non segui più questo topic.";
            }
            default -> throw new IllegalArgumentException("Azione non valida");
        };
        redirect.addFlashAttribute("success", message);
        return "redirect:" + (back != null && back.startsWith("/news") && !back.startsWith("//") ? back : "/news");
    }
}
