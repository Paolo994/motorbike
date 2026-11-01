package com.motorbike;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.motorbike.auth.AppUserDetails;
import com.motorbike.friend.FriendshipService;
import com.motorbike.geo.RoutingService;
import com.motorbike.mail.MailService;
import com.motorbike.news.FeedClient;
import com.motorbike.user.Role;
import com.motorbike.user.User;
import com.motorbike.user.UserRepository;

/**
 * Base dei test di integrazione: PostgreSQL reale (Testcontainers) e mock dei servizi esterni
 * (email, feed RSS, routing). Tutte le sottoclassi condividono lo stesso contesto Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected FriendshipService friendshipService;

    @MockitoBean protected MailService mailService;
    @MockitoBean protected FeedClient feedClient;
    @MockitoBean protected RoutingService routingService;

    /** Utente attivo con username univoco (prefisso + contatore). */
    protected User createUser(String prefix) {
        String username = prefix + SEQ.incrementAndGet();
        User u = new User(username, username.toLowerCase() + "@example.com", "{noop}x");
        u.setEnabled(true);
        return users.save(u);
    }

    protected User createAdmin(String prefix) {
        User u = createUser(prefix);
        u.setRole(Role.ADMIN);
        return users.save(u);
    }

    protected void makeFriends(User a, User b) {
        friendshipService.sendRequest(a.getId(), b.getUsername());
        friendshipService.accept(b.getId(), a.getUsername());
    }

    protected static RequestPostProcessor login(User u) {
        return user(new AppUserDetails(u));
    }
}
