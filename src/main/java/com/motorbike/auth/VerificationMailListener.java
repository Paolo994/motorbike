package com.motorbike.auth;

import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.motorbike.config.AppProperties;
import com.motorbike.mail.MailService;

@Component
class VerificationMailListener {

    private final MailService mailService;
    private final AppProperties props;

    VerificationMailListener(MailService mailService, AppProperties props) {
        this.mailService = mailService;
        this.props = props;
    }

    @Async
    @TransactionalEventListener
    void onVerificationRequested(VerificationMailRequested event) {
        String link = props.baseUrl() + "/verify?token=" + event.token();
        mailService.send(event.email(), "Conferma il tuo account MotoRide", "email/verify",
            Map.of("username", event.username(), "link", link));
    }
}
