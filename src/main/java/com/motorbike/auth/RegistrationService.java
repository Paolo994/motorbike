package com.motorbike.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.motorbike.config.AppProperties;
import com.motorbike.user.User;
import com.motorbike.user.UserRepository;

@Service
public class RegistrationService {

    public enum VerificationResult { VERIFIED, EXPIRED, INVALID }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final VerificationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public RegistrationService(UserRepository users, VerificationTokenRepository tokens,
                               PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                               AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.props = props;
    }

    @Transactional
    public User register(RegistrationForm form) {
        String username = form.getUsername().strip();
        String email = normalizeEmail(form.getEmail());

        if (users.existsByUsernameIgnoreCase(username)) {
            throw new RegistrationException("username", "Username già in uso");
        }
        if (users.existsByEmail(email)) {
            throw new RegistrationException("email", "Email già registrata");
        }

        User user = new User(username, email, passwordEncoder.encode(form.getPassword()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // registrazione concorrente con gli stessi dati
            throw new RegistrationException("username", "Username o email già in uso");
        }
        issueToken(user);
        return user;
    }

    @Transactional
    public VerificationResult verify(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return VerificationResult.INVALID;
        }
        return tokens.findByToken(tokenValue)
            .map(token -> {
                if (token.isExpired(Instant.now())) {
                    return VerificationResult.EXPIRED;
                }
                User user = token.getUser();
                user.setEnabled(true);
                tokens.deleteAllByUser(user);
                return VerificationResult.VERIFIED;
            })
            .orElse(VerificationResult.INVALID);
    }

    /** Reinvia il link. Non rivela se l'email esiste: il controller mostra sempre lo stesso messaggio. */
    @Transactional
    public void resend(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return;
        }
        users.findByEmail(normalizeEmail(rawEmail))
            .filter(u -> !u.isEnabled())
            .ifPresent(user -> {
                tokens.deleteAllByUser(user);
                issueToken(user);
            });
    }

    private void issueToken(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(props.verification().tokenValidity());
        tokens.save(new VerificationToken(value, user, expiresAt));
        // inviata solo dopo il commit, vedi VerificationMailListener
        events.publishEvent(new VerificationMailRequested(user.getEmail(), user.getUsername(), value));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
