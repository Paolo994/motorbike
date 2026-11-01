package com.motorbike.auth;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.motorbike.user.UserRepository;

/** Login con username oppure email. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        String value = login == null ? "" : login.strip();
        var user = value.contains("@")
            ? users.findByEmail(value.toLowerCase(Locale.ROOT))
            : users.findByUsernameIgnoreCase(value);
        return user.map(AppUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato"));
    }
}
