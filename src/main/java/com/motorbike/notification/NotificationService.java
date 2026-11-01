package com.motorbike.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.motorbike.user.Role;
import com.motorbike.user.User;
import com.motorbike.user.UserRepository;

/** Notifiche in-app: salvate a DB e consegnate in tempo reale via WebSocket dopo il commit. */
@Service
public class NotificationService {

    /** Payload inviato al browser. */
    public record Push(long id, String type, String title, String body, String link) {
    }

    record Created(String username, Push push) {
    }

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository notifications, UserRepository users,
                               ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.users = users;
        this.events = events;
    }

    @Transactional
    public void notify(User user, NotificationType type, String title, String body, String link) {
        Notification n = notifications.save(new Notification(user.getId(), type, title, body, link));
        events.publishEvent(new Created(user.getUsername(),
            new Push(n.getId(), type.name(), n.getTitle(), n.getBody(), n.getLink())));
    }

    @Transactional
    public void notifyAll(Collection<User> recipients, NotificationType type, String title, String body, String link) {
        recipients.forEach(u -> notify(u, type, title, body, link));
    }

    @Transactional
    public void notifyAdmins(NotificationType type, String title, String body, String link) {
        notifyAll(users.findByRoleAndEnabledTrue(Role.ADMIN), type, title, body, link);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(Long userId, int limit) {
        return notifications.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, limit));
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllRead(userId, Instant.now());
    }
}
