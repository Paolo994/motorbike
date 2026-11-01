package com.motorbike.photo;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.auth.AppUserDetails;
import com.motorbike.photo.ImageProcessor.InvalidImageException;

@Controller
public class PhotoController {

    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    /** Le foto approvate hanno un id immutabile: il browser può tenerle in cache a lungo. */
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        boolean admin = me.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return service.load(id, me.getId(), admin)
            .map(p -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.contentType()))
                .cacheControl(p.publiclyCacheable()
                    ? CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable()
                    : CacheControl.noStore())
                .body(p.data()))
            .orElseThrow(() -> new PhotoNotFoundException(id));
    }

    /** Dimensione massima accettata per una foto profilo. */
    public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    @PostMapping("/profile/photo")
    public String upload(@AuthenticationPrincipal AppUserDetails me, @RequestParam("file") MultipartFile file,
                         RedirectAttributes redirect) throws IOException {
        if (file.getSize() > MAX_PHOTO_BYTES) {
            redirect.addFlashAttribute("error", "Il file è troppo grande (massimo 5 MB).");
            return "redirect:/profile/edit";
        }
        try {
            service.upload(me.getId(), file.getBytes());
            redirect.addFlashAttribute("success",
                "Foto caricata! Sarà visibile agli altri dopo l'approvazione di un amministratore.");
        } catch (InvalidImageException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile/edit";
    }

    @PostMapping("/profile/photo/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails me, RedirectAttributes redirect) {
        service.removeAll(me.getId());
        redirect.addFlashAttribute("success", "Foto profilo rimossa.");
        return "redirect:/profile/edit";
    }

    /** File oltre il limite di spring.servlet.multipart: messaggio leggibile invece della pagina d'errore. */
    @ControllerAdvice
    static class UploadErrors {

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public String tooLarge(RedirectAttributes redirect) {
            redirect.addFlashAttribute("error", "Il file è troppo grande (massimo 5 MB).");
            return "redirect:/profile/edit";
        }
    }
}
