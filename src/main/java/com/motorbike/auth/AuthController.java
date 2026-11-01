package com.motorbike.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        return isLoggedIn(auth) ? "redirect:/" : "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication auth, Model model) {
        if (isLoggedIn(auth)) {
            return "redirect:/";
        }
        model.addAttribute("form", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult binding,
                           RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "auth/register";
        }
        try {
            var user = registrationService.register(form);
            redirect.addFlashAttribute("email", user.getEmail());
            return "redirect:/register/done";
        } catch (RegistrationException e) {
            binding.rejectValue(e.getField(), "duplicate", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/register/done")
    public String registerDone() {
        return "auth/register-done";
    }

    @GetMapping("/verify")
    public String verify(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("result", registrationService.verify(token).name());
        return "auth/verify-result";
    }

    @PostMapping("/verify/resend")
    public String resend(@RequestParam String email, RedirectAttributes redirect) {
        registrationService.resend(email);
        redirect.addFlashAttribute("info",
            "Se l'indirizzo corrisponde a un account non ancora attivo, ti abbiamo inviato un nuovo link.");
        return "redirect:/login";
    }

    private static boolean isLoggedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}
