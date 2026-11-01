package com.motorbike.photo;

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

/** Moderazione delle foto profilo. Accesso limitato a ROLE_ADMIN da SecurityConfig (/admin/**). */
@Controller
@RequestMapping("/admin/photos")
public class AdminPhotoController {

    private final PhotoService service;

    public AdminPhotoController(PhotoService service) {
        this.service = service;
    }

    @GetMapping
    public String pending(Model model) {
        model.addAttribute("photos", service.pending());
        return "admin/photos";
    }

    @PostMapping("/{id}/approve")
    public String approve(@AuthenticationPrincipal AppUserDetails admin, @PathVariable Long id,
                          RedirectAttributes redirect) {
        service.approve(admin.getId(), id);
        redirect.addFlashAttribute("success", "Foto approvata.");
        return "redirect:/admin/photos";
    }

    @PostMapping("/{id}/reject")
    public String reject(@AuthenticationPrincipal AppUserDetails admin, @PathVariable Long id,
                         @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        service.reject(admin.getId(), id, reason);
        redirect.addFlashAttribute("success", "Foto rifiutata: l'utente è stato avvisato.");
        return "redirect:/admin/photos";
    }
}
