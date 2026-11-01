package com.motorbike.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.motorbike.IntegrationTest;
import com.motorbike.notification.NotificationService;
import com.motorbike.notification.NotificationType;
import com.motorbike.user.User;

class PhotoIntegrationTest extends IntegrationTest {

    @Autowired PhotoService photos;
    @Autowired NotificationService notifications;

    @Test
    void uploadIsHiddenUntilApproved() throws Exception {
        User owner = createUser("phOwner");
        User other = createUser("phOther");
        User admin = createAdmin("phAdmin");

        mvc.perform(multipart("/profile/photo").file(png("file")).with(csrf()).with(login(owner)))
            .andExpect(redirectedUrl("/profile/edit"))
            .andExpect(flash().attribute("success", containsString("approvazione")));

        ProfilePhoto pending = photos.latestNotApproved(owner.getId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PhotoStatus.PENDING);
        assertThat(users.findById(owner.getId()).orElseThrow().getPhotoId()).isNull();
        // gli admin sono avvisati
        assertThat(notifications.latest(admin.getId(), 5)).anyMatch(n -> n.getType() == NotificationType.PHOTO_PENDING_REVIEW);

        // in attesa: la vedono il proprietario e gli admin, non gli altri
        mvc.perform(get("/photos/{id}", pending.getId()).with(login(other))).andExpect(status().isNotFound());
        mvc.perform(get("/photos/{id}", pending.getId()).with(login(owner)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/photos/{id}", pending.getId()).with(login(admin))).andExpect(status().isOk());

        // pagina di moderazione: riservata agli admin
        mvc.perform(get("/admin/photos").with(login(other))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/photos").with(login(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("@" + owner.getUsername())));
        mvc.perform(post("/admin/photos/{id}/approve", pending.getId()).with(csrf()).with(login(other)))
            .andExpect(status().isForbidden());

        mvc.perform(post("/admin/photos/{id}/approve", pending.getId()).with(csrf()).with(login(admin)))
            .andExpect(redirectedUrl("/admin/photos"));

        assertThat(users.findById(owner.getId()).orElseThrow().getPhotoId()).isEqualTo(pending.getId());
        assertThat(notifications.latest(owner.getId(), 5)).anyMatch(n -> n.getType() == NotificationType.PHOTO_APPROVED);
        mvc.perform(get("/photos/{id}", pending.getId()).with(login(other)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("immutable")));
        // il profilo mostra la foto al posto dell'iniziale
        mvc.perform(get("/u/{u}", owner.getUsername()).with(login(other)))
            .andExpect(content().string(containsString("/photos/" + pending.getId())));
    }

    @Test
    void newPhotoReplacesOldOnlyAfterApproval() throws Exception {
        User owner = createUser("phRepl");
        User admin = createAdmin("phAdm2");
        photos.upload(owner.getId(), pngBytes(Color.RED));
        Long first = photos.latestNotApproved(owner.getId()).orElseThrow().getId();
        photos.approve(admin.getId(), first);

        photos.upload(owner.getId(), pngBytes(Color.GREEN));
        Long second = photos.latestNotApproved(owner.getId()).orElseThrow().getId();
        assertThat(users.findById(owner.getId()).orElseThrow().getPhotoId()).isEqualTo(first);   // la vecchia resta visibile

        // un secondo caricamento in attesa sostituisce quello precedente (al massimo una foto in attesa)
        photos.upload(owner.getId(), pngBytes(Color.BLUE));
        Long third = photos.latestNotApproved(owner.getId()).orElseThrow().getId();
        assertThat(third).isNotEqualTo(second);
        assertThat(photos.load(second, admin.getId(), true)).isEmpty();

        photos.approve(admin.getId(), third);
        assertThat(users.findById(owner.getId()).orElseThrow().getPhotoId()).isEqualTo(third);
        assertThat(photos.load(first, admin.getId(), true)).isEmpty();                            // vecchia eliminata
    }

    @Test
    void rejectionNotifiesTheUserWithReason() throws Exception {
        User owner = createUser("phRej");
        User admin = createAdmin("phAdm3");
        photos.upload(owner.getId(), pngBytes(Color.ORANGE));
        Long id = photos.latestNotApproved(owner.getId()).orElseThrow().getId();

        mvc.perform(post("/admin/photos/{id}/reject", id).param("reason", "Non si vede il viso")
                .with(csrf()).with(login(admin)))
            .andExpect(redirectedUrl("/admin/photos"));

        ProfilePhoto rejected = photos.latestNotApproved(owner.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(PhotoStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Non si vede il viso");
        assertThat(notifications.latest(owner.getId(), 5))
            .anyMatch(n -> n.getType() == NotificationType.PHOTO_REJECTED && n.getBody().contains("Non si vede il viso"));
        mvc.perform(get("/profile/edit").with(login(owner)))
            .andExpect(content().string(containsString("Non si vede il viso")));
    }

    @Test
    void invalidUploadsShowAnError() throws Exception {
        User owner = createUser("phBad");
        mvc.perform(multipart("/profile/photo")
                .file(new MockMultipartFile("file", "virus.jpg", "image/jpeg", "MZ-not-an-image".getBytes()))
                .with(csrf()).with(login(owner)))
            .andExpect(redirectedUrl("/profile/edit"))
            .andExpect(flash().attribute("error", containsString("non è un'immagine")));
        assertThat(photos.latestNotApproved(owner.getId())).isEmpty();

        mvc.perform(multipart("/profile/photo").file(png("file")).with(login(owner)))   // senza CSRF
            .andExpect(status().isForbidden());

        byte[] big = new byte[(int) PhotoController.MAX_PHOTO_BYTES + 1];
        mvc.perform(multipart("/profile/photo").file(new MockMultipartFile("file", "big.jpg", "image/jpeg", big))
                .with(csrf()).with(login(owner)))
            .andExpect(redirectedUrl("/profile/edit"))
            .andExpect(flash().attribute("error", containsString("troppo grande")));
    }

    @Test
    void userCanRemoveTheirPhoto() throws Exception {
        User owner = createUser("phDel");
        User admin = createAdmin("phAdm4");
        photos.upload(owner.getId(), pngBytes(Color.CYAN));
        Long id = photos.latestNotApproved(owner.getId()).orElseThrow().getId();
        photos.approve(admin.getId(), id);

        mvc.perform(post("/profile/photo/delete").with(csrf()).with(login(owner)))
            .andExpect(redirectedUrl("/profile/edit"));
        assertThat(users.findById(owner.getId()).orElseThrow().getPhotoId()).isNull();
        assertThat(photos.load(id, admin.getId(), true)).isEmpty();
    }

    // ---------------------------------------------------------------- helper

    private static MockMultipartFile png(String param) throws Exception {
        return new MockMultipartFile(param, "foto.png", "image/png", pngBytes(Color.MAGENTA));
    }

    private static byte[] pngBytes(Color color) throws Exception {
        BufferedImage img = new BufferedImage(600, 450, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 600, 450);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
