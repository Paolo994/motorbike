package com.motorbike.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.motorbike.IntegrationTest;
import com.motorbike.user.User;

class AuthFlowIntegrationTest extends IntegrationTest {

    @Autowired VerificationTokenRepository tokens;
    @Autowired TransactionTemplate tx;

    @Test
    void registerVerifyAndLogin() throws Exception {
        register("Valentino46", "Vale@Example.com", "supersecret1")
            .andExpect(redirectedUrl("/register/done"));

        User created = users.findByUsernameIgnoreCase("valentino46").orElseThrow();
        assertThat(created.getEmail()).isEqualTo("vale@example.com");
        assertThat(created.isEnabled()).isFalse();
        assertThat(created.getPasswordHash()).isNotEqualTo("supersecret1");

        verify(mailService, timeout(5000))
            .send(eq("vale@example.com"), anyString(), eq("email/verify"), anyMap());

        // non ancora verificato: login bloccato
        mvc.perform(formLogin().user("Valentino46").password("supersecret1"))
            .andExpect(redirectedUrl("/login?disabled"))
            .andExpect(unauthenticated());

        String token = tokenOf(created);
        mvc.perform(get("/verify").param("token", token))
            .andExpect(status().isOk())
            .andExpect(model().attribute("result", "VERIFIED"));

        assertThat(users.findById(created.getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(tokens.findByToken(token)).isEmpty();

        // login sia con username che con email
        mvc.perform(formLogin().user("valentino46").password("supersecret1"))
            .andExpect(redirectedUrl("/"))
            .andExpect(authenticated().withUsername("Valentino46"));
        mvc.perform(formLogin().user("VALE@example.com").password("supersecret1"))
            .andExpect(authenticated().withUsername("Valentino46"));

        mvc.perform(formLogin().user("valentino46").password("wrong-password"))
            .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void rejectsDuplicateUsernameIgnoringCase() throws Exception {
        register("marc93", "marc@example.com", "supersecret1").andExpect(redirectedUrl("/register/done"));

        register("MARC93", "other@example.com", "supersecret1")
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "username"));

        register("marc_bis", "MARC@example.com", "supersecret1")
            .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    @Test
    void rejectsInvalidForm() throws Exception {
        mvc.perform(post("/register").with(csrf())
                .param("username", "a!")
                .param("email", "not-an-email")
                .param("password", "short")
                .param("confirmPassword", "different"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "username", "email", "password", "passwordConfirmed"));
    }

    @Test
    void invalidTokenIsReported() throws Exception {
        mvc.perform(get("/verify").param("token", "does-not-exist"))
            .andExpect(model().attribute("result", "INVALID"));
    }

    @Test
    void protectedPagesRequireLogin() throws Exception {
        mvc.perform(get("/profile")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void userCanEditOwnProfile() throws Exception {
        register("jorge99", "jorge@example.com", "supersecret1");
        User u = users.findByUsernameIgnoreCase("jorge99").orElseThrow();
        u.setEnabled(true);
        users.save(u);
        var principal = new AppUserDetails(users.findById(u.getId()).orElseThrow());

        mvc.perform(post("/profile/edit").with(csrf()).with(user(principal))
                .param("displayName", "Jorge")
                .param("city", "Mugello")
                .param("bikeModel", "Yamaha R1")
                .param("fuelConsumptionKmPerL", "17.5")
                .param("tankCapacityL", "17"))
            .andExpect(redirectedUrl("/u/jorge99"));

        User updated = users.findById(u.getId()).orElseThrow();
        assertThat(updated.getBikeModel()).isEqualTo("Yamaha R1");
        assertThat(updated.getFuelConsumptionKmPerL()).isEqualByComparingTo(new BigDecimal("17.5"));

        mvc.perform(get("/u/JORGE99").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Yamaha R1")));

        // valore fuori range
        mvc.perform(post("/profile/edit").with(csrf()).with(user(principal))
                .param("fuelConsumptionKmPerL", "500"))
            .andExpect(model().attributeHasFieldErrors("form", "fuelConsumptionKmPerL"));
    }

    private org.springframework.test.web.servlet.ResultActions register(String username, String email, String pwd)
        throws Exception {
        return mvc.perform(post("/register").with(csrf())
            .param("username", username)
            .param("email", email)
            .param("password", pwd)
            .param("confirmPassword", pwd));
    }

    private String tokenOf(User user) {
        return tx.execute(s -> tokens.findAll().stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .findFirst().orElseThrow().getToken());
    }
}
