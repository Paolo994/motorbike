package com.motorbike.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP su WebSocket, usato solo per il push server → browser.
 * I messaggi si inviano via HTTP POST (vedi ChatController), quindi il client non può
 * inviare nulla al broker: può solo iscriversi alla propria coda personale.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocketSecurity
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // nessun allowedOrigins: solo stessa origine
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Le sessioni WebSocket di Tomcat hanno id prevedibili: senza queste regole un client potrebbe
     * iscriversi a "/queue/messages-user{id}" di un altro utente e leggerne i messaggi.
     */
    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
        MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages
            .nullDestMatcher().authenticated()                         // CONNECT, DISCONNECT, UNSUBSCRIBE, heartbeat
            .simpSubscribeDestMatchers("/user/queue/messages", "/user/queue/notifications").authenticated()
            .anyMessage().denyAll()
            .build();
    }
}
