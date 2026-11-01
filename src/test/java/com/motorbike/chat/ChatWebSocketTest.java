package com.motorbike.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.motorbike.TestcontainersConfiguration;
import com.motorbike.friend.FriendshipService;
import com.motorbike.mail.MailService;
import com.motorbike.user.User;
import com.motorbike.user.UserRepository;

/** Test end-to-end del push in tempo reale: server vero, login via HTTP, client STOMP vero. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChatWebSocketTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "password-ws-1";

    @LocalServerPort int port;
    @Autowired ChatService chat;
    @Autowired FriendshipService friendships;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MailService mailService;

    private final WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());

    @AfterEach
    void stop() {
        stomp.stop();
    }

    @Test
    void messagesArePushedToTheRecipient() throws Exception {
        User a = newUser("wsA");
        User b = newUser("wsB");
        befriend(a, b);
        long id = chat.openDirect(a.getId(), b.getUsername());

        Browser browserA = login(a);
        StompSession session = connect(browserA, browserA.csrfToken()).get(5, TimeUnit.SECONDS);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/messages", collector(received));
        Thread.sleep(300); // lascia al broker il tempo di registrare la sottoscrizione

        chat.send(b.getId(), id, "Parto alle 8, ci vediamo al passo!");

        String json = received.poll(5, TimeUnit.SECONDS);
        assertThat(json).isNotNull()
            .contains("\"conversationId\":" + id)
            .contains("\"sender\":\"" + b.getUsername() + "\"")
            .contains("Parto alle 8");
    }

    /*
     * Nota: quando il canale inbound rifiuta un frame (InvalidCsrfTokenException sul CONNECT,
     * AccessDeniedException sul SUBSCRIBE) Spring chiude la sessione senza inviare un frame ERROR:
     * lato client si osserva solo la chiusura della connessione. Ogni test ha quindi un controllo
     * positivo che mostra che la chiusura è causata proprio dall'operazione vietata.
     */

    @Test
    void connectWithoutCsrfTokenIsRejected() throws Exception {
        Browser browser = login(newUser("wsC"));

        CompletableFuture<String> withoutToken = new CompletableFuture<>();
        connect(browser, null, closeRecorder(withoutToken));
        assertThat(withoutToken.get(5, TimeUnit.SECONDS)).contains("Connection closed");

        // controllo: stessa sessione HTTP, con il token la connessione riesce
        assertThat(connect(browser, browser.csrfToken()).get(5, TimeUnit.SECONDS).isConnected()).isTrue();
    }

    @Test
    void handshakeRequiresAuthentication() {
        Browser anonymous = new Browser(HttpClient.newBuilder().cookieHandler(new CookieManager()).build(), null, "");
        CompletableFuture<StompSession> future = connect(anonymous, "whatever");
        // senza sessione Spring Security redirige al login (il client segue il redirect): niente upgrade
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .rootCause().hasMessageContaining("did not permit the HTTP upgrade");
    }

    @Test
    void subscribingToRawBrokerQueuesIsDenied() throws Exception {
        Browser browser = login(newUser("wsD"));
        CompletableFuture<String> closed = new CompletableFuture<>();
        StompSession session = connect(browser, browser.csrfToken(), closeRecorder(closed)).get(5, TimeUnit.SECONDS);

        // controllo: l'iscrizione alla propria coda è permessa e la sessione resta aperta
        session.subscribe("/user/queue/messages", collector(new LinkedBlockingQueue<>()));
        Thread.sleep(500);
        assertThat(closed).isNotDone();

        // tentativo di intercettare la coda di un'altra sessione: la connessione viene chiusa
        session.subscribe("/queue/messages-user0", collector(new LinkedBlockingQueue<>()));
        assertThat(closed.get(5, TimeUnit.SECONDS)).contains("Connection closed");
    }

    private static StompSessionHandlerAdapter closeRecorder(CompletableFuture<String> closed) {
        return new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                closed.complete(String.valueOf(ex.getMessage()));
            }
        };
    }


    // ---------------------------------------------------------------- helper

    private record Browser(HttpClient http, String sessionCookie, String csrfToken) {
    }

    /** Login via form come farebbe un browser; restituisce cookie di sessione e token CSRF della pagina. */
    private Browser login(User user) throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER).build();

        String loginPage = http.send(HttpRequest.newBuilder(url("/login")).build(),
            HttpResponse.BodyHandlers.ofString()).body();
        String formToken = extract(loginPage, "name=\"_csrf\" value=\"([^\"]+)\"");

        String form = "username=" + user.getUsername() + "&password=" + PASSWORD + "&_csrf=" + formToken;
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(url("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(res.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).doesNotContain("error"));

        String page = http.send(HttpRequest.newBuilder(url("/chat")).build(), HttpResponse.BodyHandlers.ofString()).body();
        String metaToken = extract(page, "<meta name=\"_csrf\" content=\"([^\"]+)\"");

        String sessionCookie = cookies.getCookieStore().getCookies().stream()
            .filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().toString();
        return new Browser(http, sessionCookie, metaToken);
    }

    private CompletableFuture<StompSession> connect(Browser browser, String csrfToken) {
        return connect(browser, csrfToken, new StompSessionHandlerAdapter() { });
    }

    private CompletableFuture<StompSession> connect(Browser browser, String csrfToken,
                                                    StompSessionHandlerAdapter handler) {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        if (browser.sessionCookie() != null) {
            handshake.add("Cookie", browser.sessionCookie());
        }
        StompHeaders connectHeaders = new StompHeaders();
        if (csrfToken != null) {
            connectHeaders.add("X-CSRF-TOKEN", csrfToken);
        }
        return stomp.connectAsync("ws://localhost:" + port + "/ws", handshake, connectHeaders, handler);
    }

    private static StompFrameHandler collector(BlockingQueue<String> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        };
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String extract(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        assertThat(m.find()).as("pattern %s", regex).isTrue();
        return m.group(1);
    }

    private User newUser(String name) {
        String username = name + SEQ.incrementAndGet();
        User u = new User(username, username.toLowerCase() + "@example.com", encoder.encode(PASSWORD));
        u.setEnabled(true);
        return users.save(u);
    }

    private void befriend(User a, User b) {
        friendships.sendRequest(a.getId(), b.getUsername());
        friendships.accept(b.getId(), a.getUsername());
    }
}
