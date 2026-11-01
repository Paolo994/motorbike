package com.motorbike.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class NotificationPushListener {

    static final String USER_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messaging;

    NotificationPushListener(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener
    void onCreated(NotificationService.Created event) {
        messaging.convertAndSendToUser(event.username(), USER_QUEUE, event.push());
    }
}
