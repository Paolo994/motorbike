package com.motorbike.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.motorbike.user.User;

@Entity
@Table(name = "conversation_participants")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected Participant() {
    }

    /** Avanza il segno di lettura (non torna mai indietro). */
    void markReadUpTo(Long messageId) {
        if (messageId != null && (lastReadMessageId == null || messageId > lastReadMessageId)) {
            lastReadMessageId = messageId;
        }
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getUser() { return user; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
}
