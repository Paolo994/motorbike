package com.motorbike.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.motorbike.IntegrationTest;
import com.motorbike.auth.AppUserDetails;
import com.motorbike.user.User;
import com.motorbike.user.UserNotFoundException;

class FriendshipIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired FriendshipService service;

    @Test
    void requestAcceptAndRemove() throws Exception {
        User a = newUser("anna");
        User b = newUser("bruno");

        mvc.perform(post("/friends/{u}/request", b.getUsername()).with(csrf()).with(as(a)))
            .andExpect(redirectedUrl("/friends"))
            .andExpect(flash().attributeExists("success"));

        assertThat(service.relation(a.getId(), b.getId())).isEqualTo(Relation.OUTGOING);
        assertThat(service.relation(b.getId(), a.getId())).isEqualTo(Relation.INCOMING);
        assertThat(service.countIncoming(b.getId())).isEqualTo(1);

        // la pagina di B mostra la richiesta e il badge nella navbar
        mvc.perform(get("/friends").with(as(b)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Richieste ricevute")))
            .andExpect(content().string(containsString("/friends/" + a.getUsername() + "/accept")));

        // A non può accettare la propria richiesta
        mvc.perform(post("/friends/{u}/accept", b.getUsername()).with(csrf()).with(as(a)))
            .andExpect(flash().attributeExists("error"));

        mvc.perform(post("/friends/{u}/accept", a.getUsername()).with(csrf()).with(as(b))
                .param("back", "/u/" + a.getUsername()))
            .andExpect(redirectedUrl("/u/" + a.getUsername()));

        assertThat(service.areFriends(a.getId(), b.getId())).isTrue();
        assertThat(service.areFriends(b.getId(), a.getId())).isTrue();
        assertThat(service.countIncoming(b.getId())).isZero();
        assertThat(service.overview(a.getId()).friends())
            .extracting(UserCard::username).containsExactly(b.getUsername());

        service.remove(a.getId(), b.getUsername());
        assertThat(service.relation(b.getId(), a.getId())).isEqualTo(Relation.NONE);
    }

    @Test
    void crossedRequestsBecomeFriendship() {
        User a = newUser("carla");
        User b = newUser("dario");
        assertThat(service.sendRequest(a.getId(), b.getUsername())).isEqualTo(Relation.OUTGOING);
        assertThat(service.sendRequest(b.getId(), a.getUsername())).isEqualTo(Relation.FRIENDS);
        assertThat(service.areFriends(a.getId(), b.getId())).isTrue();
    }

    @Test
    void invalidRequestsAreRejected() {
        User a = newUser("elena");
        User b = newUser("fabio");

        assertThatThrownBy(() -> service.sendRequest(a.getId(), a.getUsername()))
            .isInstanceOf(FriendshipException.class);

        service.sendRequest(a.getId(), b.getUsername());
        assertThatThrownBy(() -> service.sendRequest(a.getId(), b.getUsername().toUpperCase()))
            .isInstanceOf(FriendshipException.class);

        assertThatThrownBy(() -> service.sendRequest(a.getId(), "nessuno_esiste"))
            .isInstanceOf(UserNotFoundException.class);

        User disabled = newUser("gino");
        disabled.setEnabled(false);
        users.save(disabled);
        assertThatThrownBy(() -> service.sendRequest(a.getId(), disabled.getUsername()))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void declineAndCancelAllowNewRequests() {
        User a = newUser("ivo");
        User b = newUser("luca");

        service.sendRequest(a.getId(), b.getUsername());
        service.decline(b.getId(), a.getUsername());
        assertThat(service.relation(a.getId(), b.getId())).isEqualTo(Relation.NONE);

        service.sendRequest(a.getId(), b.getUsername());
        service.cancel(a.getId(), b.getUsername());
        assertThat(service.relation(a.getId(), b.getId())).isEqualTo(Relation.NONE);

        // dopo un rifiuto si può riprovare
        assertThat(service.sendRequest(a.getId(), b.getUsername())).isEqualTo(Relation.OUTGOING);
    }

    @Test
    void searchFindsUsersWithRelationAndPrefixFirst() throws Exception {
        String tag = "zq" + SEQ.incrementAndGet();
        User me = save(tag + "_me", true);
        User prefix = save(tag + "rider", true);
        User middle = save("x" + tag + "rider", true);
        save(tag + "hidden", false);                     // non verificato: escluso
        service.sendRequest(me.getId(), prefix.getUsername());

        var results = service.search(me.getId(), tag.toUpperCase());
        assertThat(results).extracting(UserCard::username)
            .containsExactly(prefix.getUsername(), middle.getUsername());   // né me stesso né l'utente disabilitato
        assertThat(results).extracting(UserCard::relation)
            .containsExactly(Relation.OUTGOING, Relation.NONE);

        // '_' è un carattere letterale, non un jolly LIKE
        assertThat(service.search(me.getId(), tag + "_")).isEmpty();
        assertThat(service.search(me.getId(), "z")).isEmpty();              // troppo corta

        mvc.perform(get("/friends/search").param("q", tag).with(as(me)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("@<span>" + middle.getUsername() + "</span>")))
            .andExpect(content().string(containsString("Annulla richiesta")))
            .andExpect(content().string(not(containsString(tag + "hidden"))));
    }

    @Test
    void profileShowsFriendshipActions() throws Exception {
        User a = newUser("marco");
        User b = newUser("nadia");
        mvc.perform(get("/u/{u}", b.getUsername()).with(as(a)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/friends/" + b.getUsername() + "/request")));
    }

    @Test
    void unknownUserGives404() throws Exception {
        User a = newUser("olga");
        mvc.perform(post("/friends/{u}/request", "ghost_rider_404").with(csrf()).with(as(a)))
            .andExpect(status().isNotFound());
    }

    @Test
    void backParameterCannotRedirectOutside() {
        assertThat(FriendController.safeBack("/friends/search?q=ab")).isEqualTo("/friends/search?q=ab");
        assertThat(FriendController.safeBack("//evil.com")).isEqualTo("/friends");
        assertThat(FriendController.safeBack("https://evil.com")).isEqualTo("/friends");
        assertThat(FriendController.safeBack("/\\evil.com")).isEqualTo("/friends");
        assertThat(FriendController.safeBack(null)).isEqualTo("/friends");
    }

    // ---------------------------------------------------------------- helper

    private User newUser(String name) {
        return save(name + SEQ.incrementAndGet(), true);
    }

    private User save(String username, boolean enabled) {
        User u = new User(username, username.toLowerCase() + "@example.com", "{noop}x");
        u.setEnabled(enabled);
        return users.save(u);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(User u) {
        return user(new AppUserDetails(u));
    }
}
