package com.motorbike.mail;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.motorbike.config.AppProperties;

import jakarta.mail.MessagingException;

/** Invio email HTML da template Thymeleaf (templates/email/*). Riutilizzato anche per le notifiche news. */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties props;

    public MailService(JavaMailSender mailSender, TemplateEngine templateEngine, AppProperties props) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.props = props;
    }

    public void send(String to, String subject, String template, Map<String, Object> variables) {
        Context ctx = new Context();
        ctx.setVariables(variables);
        ctx.setVariable("baseUrl", props.baseUrl());
        String html = templateEngine.process(template, ctx);
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            // l'invio è asincrono: logghiamo, l'utente può richiedere un nuovo link
            log.error("Invio email '{}' a {} fallito", subject, to, e);
        }
    }
}
