package com.motorbike.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consegna i nuovi messaggi via WebSocket a tutti i partecipanti, mittente compreso
 * (così si aggiornano anche le sue altre schede). Avviene solo dopo il commit.
 */
@Component
class ChatPushListener {

    static final String USER_QUEUE = "/queue/messages";

    private final SimpMessagingTemplate messaging;

    ChatPushListener(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener
    void onMessageSent(MessageSentEvent event) {
        for (String username : event.recipientUsernames()) {
            messaging.convertAndSendToUser(username, USER_QUEUE, event.message());
        }
    }
}
