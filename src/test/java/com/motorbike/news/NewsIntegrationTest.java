package com.motorbike.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.motorbike.IntegrationTest;
import com.motorbike.notification.NotificationService;
import com.motorbike.notification.NotificationType;
import com.motorbike.user.User;

class NewsIntegrationTest extends IntegrationTest {

    private static final String GPONE = "https://www.gpone.com/it/rss.xml";
    private static final String WSBK_EN = "https://www.motorsport.com/rss/wsbk/news/";

    @Autowired NewsIngestService ingest;
    @Autowired NewsService news;
    @Autowired NotificationService notifications;
    @Autowired JdbcClient jdbc;

    /**
     * Ciclo completo dell'agente: primo import silenzioso, poi notifiche e una sola email riepilogativa
     * solo per gli articoli nuovi e recenti dei topic seguiti.
     */
    @Test
    void agentImportsClassifiesAndNotifies() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User emailFan = createUser("newsMail");
        User appOnly = createUser("newsApp");
        User sbkFan = createUser("newsSbk");
        news.follow(emailFan.getId(), "motogp", true);
        news.follow(appOnly.getId(), "motogp", false);
        news.follow(sbkFan.getId(), "superbike", true);

        FeedItem bagnaia = item(run, "Bagnaia vince il GP di Sepang", Duration.ofHours(3));
        FeedItem bulega = item(run, "Bulega campione del mondo Superbike", Duration.ofHours(2));
        List<FeedItem> gponeFeed = new ArrayList<>(List.of(bagnaia, bulega));
        when(feedClient.fetch(anyString())).thenAnswer(inv -> GPONE.equals(inv.getArgument(0)) ? gponeFeed : List.of());

        // 1) primo import: articoli salvati ma nessuna notifica
        var first = ingest.fetchAll();
        assertThat(first.newArticles()).isEqualTo(2);
        assertThat(first.notifiedArticles()).isZero();
        assertThat(notifications.latest(emailFan.getId(), 10)).isEmpty();
        verify(mailService, never()).send(anyString(), anyString(), eq("email/news-digest"), anyMap());

        // 2) secondo giro: duplicati ignorati, 1 nuovo MotoGP recente, 1 MotoGP vecchio, 1 modello; una fonte in errore
        FeedItem marquez = item(run, "Marquez in pole a Valencia", Duration.ofMinutes(30));
        FeedItem old = item(run, "Bagnaia ricorda la vittoria del 2022", Duration.ofDays(5));
        FeedItem panigale = item(run, "Nuova Ducati Panigale: la prova in pista", Duration.ofHours(1));
        gponeFeed.addAll(List.of(marquez, old, panigale));
        when(feedClient.fetch(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            if (WSBK_EN.equals(url)) {
                throw new IOException("HTTP 503");
            }
            return GPONE.equals(url) ? gponeFeed : List.of();
        });

        var second = ingest.fetchAll();
        assertThat(second.newArticles()).isEqualTo(3);
        assertThat(second.notifiedArticles()).isEqualTo(2);            // marquez + panigale (old è troppo vecchio)
        assertThat(second.failedSources()).isEqualTo(1);

        // classificazione: GPOne ha topic predefinito MotoGP, ma le parole chiave prevalgono
        assertThat(topicOf(bulega)).isEqualTo("superbike");
        assertThat(topicOf(panigale)).isEqualTo("modelli");
        assertThat(topicOf(marquez)).isEqualTo("motogp");

        // notifiche in-app: una per topic, con il titolo dell'ultimo articolo
        assertThat(notifications.latest(emailFan.getId(), 10)).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.NEWS);
            assertThat(n.getTitle()).isEqualTo("Nuovo articolo: MotoGP");
            assertThat(n.getBody()).isEqualTo(marquez.title());
            assertThat(n.getLink()).isEqualTo("/news?topic=motogp");
        });
        assertThat(notifications.latest(appOnly.getId(), 10)).hasSize(1);
        assertThat(notifications.latest(sbkFan.getId(), 10)).isEmpty();   // nessun nuovo articolo Superbike

        // email: solo a chi l'ha attivata, una sola con il riepilogo
        verify(mailService).send(eq(emailFan.getEmail()), eq("MotoRide · 1 nuova notizia per te"),
            eq("email/news-digest"), argThat((Map<String, Object> m) -> {
                @SuppressWarnings("unchecked")
                var sections = (List<NewsNotifier.DigestSection>) m.get("sections");
                return sections.size() == 1 && sections.getFirst().articles().getFirst().title().equals(marquez.title());
            }));
        verify(mailService, never()).send(eq(appOnly.getEmail()), any(), any(), any());

        // stato delle fonti
        String status = jdbc.sql("select last_status from news_sources where feed_url = :u").param("u", WSBK_EN)
            .query(String.class).single();
        assertThat(status).isEqualTo("ERROR");

        // pagina notizie filtrata per topic
        mvc.perform(get("/news").param("topic", "motogp").with(login(emailFan)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(marquez.title())))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(panigale.title()))));
        mvc.perform(get("/news").with(login(emailFan)))
            .andExpect(content().string(containsString(panigale.title())));
    }

    @Test
    void userManagesSubscriptions() throws Exception {
        User u = createUser("newsSub");
        mvc.perform(post("/news/subscriptions").param("topic", "modelli").param("action", "follow")
                .with(csrf()).with(login(u)))
            .andExpect(redirectedUrl("/news"));
        assertThat(news.subscriptions(u.getId()).values()).singleElement()
            .satisfies(s -> assertThat(s.isEmailEnabled()).isTrue());

        mvc.perform(post("/news/subscriptions").param("topic", "modelli").param("action", "email-off")
                .param("back", "/news?topic=modelli").with(csrf()).with(login(u)))
            .andExpect(redirectedUrl("/news?topic=modelli"));
        assertThat(news.subscriptions(u.getId()).values()).singleElement()
            .satisfies(s -> assertThat(s.isEmailEnabled()).isFalse());

        mvc.perform(post("/news/subscriptions").param("topic", "modelli").param("action", "unfollow")
                .param("back", "https://evil.example").with(csrf()).with(login(u)))
            .andExpect(redirectedUrl("/news"));                       // redirect esterno ignorato
        assertThat(news.subscriptions(u.getId())).isEmpty();

        mvc.perform(post("/news/subscriptions").param("topic", "inesistente").param("action", "follow")
                .with(csrf()).with(login(u)))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminPagesAreProtected() throws Exception {
        User u = createUser("newsUser");
        User admin = createAdmin("newsAdm");
        mvc.perform(get("/admin/news").with(login(u))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/news").with(login(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("GPOne")));
        mvc.perform(get("/admin").with(login(admin))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helper

    private static FeedItem item(String run, String title, Duration age) {
        return new FeedItem(title, "https://test.example/" + run + "/" + Math.abs(title.hashCode()), "Riassunto: " + title,
            null, Instant.now().minus(age));
    }

    private String topicOf(FeedItem item) {
        return jdbc.sql("select t.slug from news_articles a join news_topics t on t.id = a.topic_id where a.url = :u")
            .param("u", item.url()).query(String.class).single();
    }
}
