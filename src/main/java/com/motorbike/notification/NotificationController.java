package com.motorbike.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.motorbike.auth.AppUserDetails;

@Controller
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Mostra le ultime notifiche e le segna come lette. */
    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("notifications", service.latest(me.getId(), 50));
        service.markAllRead(me.getId());
        model.addAttribute("unreadNotifications", 0L);
        return "notifications/index";
    }

    @PostMapping("/notifications/read")
    public String markAllRead(@AuthenticationPrincipal AppUserDetails me) {
        service.markAllRead(me.getId());
        return "redirect:/notifications";
    }
}
