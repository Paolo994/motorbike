package com.motorbike.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.motorbike.auth.AppUserDetails;
import com.motorbike.chat.ChatService;
import com.motorbike.friend.FriendshipService;
import com.motorbike.notification.NotificationService;
import com.motorbike.photo.PhotoService;

/** Attributi comuni a tutte le pagine (badge della navbar). */
@ControllerAdvice
public class GlobalModelAdvice {

    private final FriendshipService friendships;
    private final ChatService chat;
    private final NotificationService notifications;
    private final PhotoService photos;

    public GlobalModelAdvice(FriendshipService friendships, ChatService chat, NotificationService notifications,
                             PhotoService photos) {
        this.friendships = friendships;
        this.chat = chat;
        this.notifications = notifications;
        this.photos = photos;
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications(@AuthenticationPrincipal AppUserDetails me) {
        return me == null ? 0 : notifications.unreadCount(me.getId());
    }

    /** Foto da moderare: calcolato solo per gli admin. */
    @ModelAttribute("pendingPhotos")
    public long pendingPhotos(@AuthenticationPrincipal AppUserDetails me) {
        boolean admin = me != null && me.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return admin ? photos.pendingCount() : 0;
    }

    @ModelAttribute("pendingFriendRequests")
    public long pendingFriendRequests(@AuthenticationPrincipal AppUserDetails me) {
        return me == null ? 0 : friendships.countIncoming(me.getId());
    }

    @ModelAttribute("unreadMessages")
    public long unreadMessages(@AuthenticationPrincipal AppUserDetails me) {
        return me == null ? 0 : chat.unreadTotal(me.getId());
    }
}
