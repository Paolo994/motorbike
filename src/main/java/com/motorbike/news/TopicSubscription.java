package com.motorbike.news;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topic_subscriptions")
public class TopicSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TopicSubscription() {
    }

    TopicSubscription(Long userId, Long topicId, boolean emailEnabled) {
        this.userId = userId;
        this.topicId = topicId;
        this.emailEnabled = emailEnabled;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTopicId() { return topicId; }
    public boolean isEmailEnabled() { return emailEnabled; }

    void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
}
