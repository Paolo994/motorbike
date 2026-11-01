package com.motorbike.news;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/news")
public class AdminNewsController {

    private final NewsAdminService admin;
    private final NewsIngestService ingest;
    private final NewsService news;

    public AdminNewsController(NewsAdminService admin, NewsIngestService ingest, NewsService news) {
        this.admin = admin;
        this.ingest = ingest;
        this.news = news;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("sources", admin.sources());
        model.addAttribute("counts", admin.articleCounts());
        model.addAttribute("topics", news.topics());
        model.addAttribute("running", ingest.isRunning());
        return "admin/news";
    }

    @PostMapping("/run")
    public String run(RedirectAttributes redirect) {
        if (ingest.isRunning()) {
            redirect.addFlashAttribute("info", "L'agente sta già leggendo le fonti.");
        } else {
            ingest.fetchAllAsync();
            redirect.addFlashAttribute("success", "Agente avviato: ricarica la pagina tra qualche secondo.");
        }
        return "redirect:/admin/news";
    }

    @PostMapping("/sources")
    public String addSource(@RequestParam String name, @RequestParam String feedUrl,
                            @RequestParam(required = false) String siteUrl, @RequestParam String language,
                            @RequestParam(required = false) String defaultTopic, RedirectAttributes redirect) {
        try {
            admin.addSource(name, feedUrl, siteUrl, language, defaultTopic);
            redirect.addFlashAttribute("success", "Fonte aggiunta: verrà letta al prossimo giro dell'agente.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/news";
    }

    @PostMapping("/sources/{id}/enabled")
    public String toggle(@PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        admin.setEnabled(id, enabled);
        redirect.addFlashAttribute("success", enabled ? "Fonte attivata." : "Fonte disattivata.");
        return "redirect:/admin/news";
    }

    @PostMapping("/topics/{slug}/keywords")
    public String keywords(@PathVariable String slug, @RequestParam String keywords, RedirectAttributes redirect) {
        try {
            admin.updateKeywords(slug, keywords);
            redirect.addFlashAttribute("success", "Parole chiave aggiornate: valgono per i prossimi articoli.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/news#topics";
    }
}
