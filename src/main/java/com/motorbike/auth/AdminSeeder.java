package com.motorbike.auth;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.motorbike.config.AppProperties;
import com.motorbike.user.Role;
import com.motorbike.user.User;
import com.motorbike.user.UserRepository;

/** Crea l'utente admin all'avvio se ADMIN_PASSWORD è impostata e l'utente non esiste ancora. */
@Component
class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var admin = props.admin();
        if (admin == null || admin.password() == null || admin.password().isBlank()) {
            return;
        }
        if (users.existsByUsernameIgnoreCase(admin.username())) {
            return;
        }
        User user = new User(admin.username(), admin.email().toLowerCase(Locale.ROOT),
            passwordEncoder.encode(admin.password()));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        users.save(user);
        log.info("Creato utente admin '{}'", admin.username());
    }
}
