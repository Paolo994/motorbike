package com.motorbike.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.motorbike.IntegrationTest;
import com.motorbike.auth.AppUserDetails;
import com.motorbike.friend.FriendshipService;
import com.motorbike.friend.UserCard;
import com.motorbike.user.User;

class ChatIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ChatService chat;
    @Autowired FriendshipService friendships;

    @Test
    void searchFiltersConversationsAndSuggestsFriends() throws Exception {
        User me = newUser("srchMe");
        User alpha = newUser("srchAlpha");
        User beta = newUser("srchBeta");
        makeFriends(me, alpha);
        makeFriends(me, beta);
        long withAlpha = chat.openDirect(me.getId(), alpha.getUsername());
        chat.send(alpha.getId(), withAlpha, "Ciao!");

        // "alpha": trova la conversazione, nessun amico aggiuntivo
        mvc.perform(get("/chat").param("q", "ALPHA").with(as(me)))
            .andExpect(status().isOk())
            .andExpect(model().attribute("conversations", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(model().attribute("friendMatches", org.hamcrest.Matchers.empty()))
            .andExpect(content().string(containsString("@<span>" + alpha.getUsername() + "</span>")));

        // "beta": nessuna conversazione, ma l'amico viene proposto con il link per scrivergli
        mvc.perform(get("/chat").param("q", "srchbeta").with(as(me)))
            .andExpect(model().attribute("conversations", org.hamcrest.Matchers.empty()))
            .andExpect(result -> {
                @SuppressWarnings("unchecked")
                var matches = (java.util.List<UserCard>) result.getModelAndView().getModel().get("friendMatches");
                assertThat(matches).extracting(UserCard::username).containsExactly(beta.getUsername());
            })
            .andExpect(content().string(containsString("/chat/with/" + beta.getUsername())));

        // il prefisso comune trova entrambi
        mvc.perform(get("/chat").param("q", "srch").with(as(me)))
            .andExpect(model().attribute("conversations", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(model().attribute("friendMatches", org.hamcrest.Matchers.hasSize(1)));

        mvc.perform(get("/chat").param("q", "nessuno-cosi").with(as(me)))
            .andExpect(content().string(containsString("Nessun amico trovato")));
    }

    @Test
    void onlyFriendsCanOpenAChat() throws Exception {
        User a = newUser("chA");
        User b = newUser("chB");
        mvc.perform(get("/chat/with/{u}", b.getUsername()).with(as(a)))
            .andExpect(redirectedUrl("/u/" + b.getUsername()))
            .andExpect(flash().attribute("error", "Puoi chattare solo con i tuoi amici."));
    }

    @Test
    void directConversationIsUniquePerPair() throws Exception {
        User[] p = friends("chC", "chD");
        long id = chat.openDirect(p[0].getId(), p[1].getUsername());
        assertThat(chat.openDirect(p[1].getId(), p[0].getUsername().toUpperCase())).isEqualTo(id);

        mvc.perform(get("/chat/with/{u}", p[1].getUsername()).with(as(p[0])))
            .andExpect(redirectedUrl("/chat/" + id));
    }

    @Test
    void sendReadAndUnreadCounters() throws Exception {
        User[] p = friends("chE", "chF");
        User a = p[0], b = p[1];
        long id = chat.openDirect(a.getId(), b.getUsername());

        mvc.perform(post("/chat/{id}/messages", id).with(csrf()).with(as(a))
                .accept(MediaType.APPLICATION_JSON)
                .param("body", "  Ciao! Giro al Mugello domenica? <script>alert(1)</script>  "))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.conversationId").value(id))
            .andExpect(jsonPath("$.sender").value(a.getUsername()))
            .andExpect(jsonPath("$.body").value("Ciao! Giro al Mugello domenica? <script>alert(1)</script>"));

        assertThat(chat.unreadTotal(b.getId())).isEqualTo(1);
        assertThat(chat.unreadTotal(a.getId())).isZero();                     // i propri messaggi non contano

        var inboxB = chat.inbox(b.getId());
        assertThat(inboxB).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(id);
            assertThat(c.otherUsername()).isEqualTo(a.getUsername());
            assertThat(c.unread()).isEqualTo(1);
            assertThat(c.lastFromMe()).isFalse();
            assertThat(c.lastBody()).startsWith("Ciao!");
        });

        // la navbar mostra il badge; aprendo la conversazione i messaggi diventano letti
        mvc.perform(get("/chat").with(as(b)))
            .andExpect(content().string(containsString("Giro al Mugello")));
        mvc.perform(get("/chat/{id}", id).with(as(b)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))  // escapato
            .andExpect(content().string(containsString("id=\"chat-form\"")))
            .andExpect(model().attribute("unreadMessages", 0L));                  // badge già aggiornato
        assertThat(chat.unreadTotal(b.getId())).isZero();

        // risposta di B, poi A segna come letto via API
        chat.send(b.getId(), id, "Ci sto!");
        assertThat(chat.unreadTotal(a.getId())).isEqualTo(1);
        mvc.perform(post("/chat/{id}/read", id).with(csrf()).with(as(a))).andExpect(status().isNoContent());
        assertThat(chat.unreadTotal(a.getId())).isZero();
        assertThat(chat.inbox(a.getId()).getFirst().lastBody()).isEqualTo("Ci sto!");
    }

    @Test
    void outsidersCannotSeeOrWrite() throws Exception {
        User[] p = friends("chG", "chH");
        User outsider = newUser("chI");
        long id = chat.openDirect(p[0].getId(), p[1].getUsername());

        mvc.perform(get("/chat/{id}", id).with(as(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/chat/{id}/messages", id).param("before", "999999").with(as(outsider))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
        mvc.perform(post("/chat/{id}/messages", id).with(csrf()).with(as(outsider))
                .accept(MediaType.APPLICATION_JSON).param("body", "intruso"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/chat/{id}/messages", id).with(as(p[0]))           // senza CSRF
                .accept(MediaType.APPLICATION_JSON).param("body", "x"))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        User[] p = friends("chJ", "chK");
        long id = chat.openDirect(p[0].getId(), p[1].getUsername());

        mvc.perform(post("/chat/{id}/messages", id).with(csrf()).with(as(p[0]))
                .accept(MediaType.APPLICATION_JSON).param("body", "   "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Il messaggio è vuoto."));
        mvc.perform(post("/chat/{id}/messages", id).with(csrf()).with(as(p[0]))
                .accept(MediaType.APPLICATION_JSON).param("body", "x".repeat(Message.MAX_LENGTH + 1)))
            .andExpect(status().isBadRequest());

        // fallback senza JavaScript: redirect con messaggio d'errore
        mvc.perform(post("/chat/{id}/messages", id).with(csrf()).with(as(p[0])).param("body", ""))
            .andExpect(redirectedUrl("/chat/" + id))
            .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/chat/{id}/messages", id).with(csrf()).with(as(p[0])).param("body", "via form"))
            .andExpect(redirectedUrl("/chat/" + id));
        assertThat(chat.inbox(p[1].getId()).getFirst().lastBody()).isEqualTo("via form");
    }

    @Test
    void historyStaysReadableAfterUnfriendButSendingIsBlocked() throws Exception {
        User[] p = friends("chL", "chM");
        long id = chat.openDirect(p[0].getId(), p[1].getUsername());
        chat.send(p[0].getId(), id, "Prima del litigio");

        friendships.remove(p[1].getId(), p[0].getUsername());

        mvc.perform(post("/chat/{id}/messages", id).with(csrf()).with(as(p[0]))
                .accept(MediaType.APPLICATION_JSON).param("body", "Dopo"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/chat/{id}", id).with(as(p[1])))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Prima del litigio")))
            .andExpect(content().string(containsString("Non siete più amici")));
        assertThat(chat.view(p[1].getId(), id).canSend()).isFalse();
    }

    @Test
    void olderMessagesArePaginated() throws Exception {
        User[] p = friends("chN", "chO");
        long id = chat.openDirect(p[0].getId(), p[1].getUsername());
        for (int i = 1; i <= ChatService.PAGE_SIZE + 5; i++) {
            chat.send(p[i % 2].getId(), id, "msg " + i);
        }

        ConversationView view = chat.view(p[0].getId(), id);
        assertThat(view.hasOlder()).isTrue();
        assertThat(view.messages()).hasSize(ChatService.PAGE_SIZE);
        assertThat(view.messages().getFirst().body()).isEqualTo("msg 6");
        assertThat(view.messages().getLast().body()).isEqualTo("msg 55");

        mvc.perform(get("/chat/{id}/messages", id).param("before", String.valueOf(view.messages().getFirst().id()))
                .with(as(p[0])).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].body").value("msg 1"))
            .andExpect(jsonPath("$[4].body").value("msg 5"));
    }

    // ---------------------------------------------------------------- helper

    private User newUser(String name) {
        String username = name + SEQ.incrementAndGet();
        User u = new User(username, username.toLowerCase() + "@example.com", "{noop}x");
        u.setEnabled(true);
        return users.save(u);
    }

    private User[] friends(String a, String b) {
        User ua = newUser(a);
        User ub = newUser(b);
        friendships.sendRequest(ua.getId(), ub.getUsername());
        friendships.accept(ub.getId(), ua.getUsername());
        return new User[] {ua, ub};
    }

    private static RequestPostProcessor as(User u) {
        return user(new AppUserDetails(u));
    }
}
