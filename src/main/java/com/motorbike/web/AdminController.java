package com.motorbike.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.fuel.FuelImportService;
import com.motorbike.fuel.FuelPriceService;
import com.motorbike.news.NewsAdminService;
import com.motorbike.photo.PhotoService;

/** Pannello di amministrazione (accesso limitato a ROLE_ADMIN da SecurityConfig). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final PhotoService photos;
    private final NewsAdminService news;
    private final FuelImportService fuelImport;
    private final FuelPriceService fuelPrices;

    public AdminController(PhotoService photos, NewsAdminService news, FuelImportService fuelImport,
                           FuelPriceService fuelPrices) {
        this.photos = photos;
        this.news = news;
        this.fuelImport = fuelImport;
        this.fuelPrices = fuelPrices;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("pendingPhotoCount", photos.pendingCount());
        model.addAttribute("newsStats", news.stats());
        model.addAttribute("fuelRun", fuelImport.lastRun().orElse(null));
        model.addAttribute("fuelStations", fuelImport.stationCount());
        model.addAttribute("fuelAverage", fuelPrices.nationalAverage().orElse(null));
        model.addAttribute("fuelRunning", fuelImport.isRunning());
        return "admin/index";
    }

    @PostMapping("/fuel/import")
    public String importFuel(RedirectAttributes redirect) {
        if (fuelImport.isRunning()) {
            redirect.addFlashAttribute("info", "Import dei prezzi già in corso.");
        } else {
            fuelImport.importAllAsync();
            redirect.addFlashAttribute("success", "Import dei prezzi avviato: richiede circa un minuto.");
        }
        return "redirect:/admin";
    }
}
