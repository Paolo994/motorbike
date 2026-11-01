package com.motorbike.news;

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

@Entity
@Table(name = "news_sources")
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "feed_url", nullable = false, unique = true, length = 500)
    private String feedUrl;

    @Column(name = "site_url", length = 500)
    private String siteUrl;

    @Column(nullable = false, length = 5)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_topic_id")
    private NewsTopic defaultTopic;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "last_new_count")
    private Integer lastNewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NewsSource() {
    }

    public NewsSource(String name, String feedUrl, String siteUrl, String language, NewsTopic defaultTopic) {
        this.name = name;
        this.feedUrl = feedUrl;
        this.siteUrl = siteUrl;
        this.language = language;
        this.defaultTopic = defaultTopic;
        this.createdAt = Instant.now();
    }

    void recordSuccess(Instant at, int newArticles) {
        this.lastFetchedAt = at;
        this.lastStatus = "OK";
        this.lastError = null;
        this.lastNewCount = newArticles;
    }

    void recordFailure(Instant at, String error) {
        this.lastFetchedAt = at;
        this.lastStatus = "ERROR";
        this.lastError = error == null ? "Errore sconosciuto" : error.substring(0, Math.min(500, error.length()));
        this.lastNewCount = 0;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getFeedUrl() { return feedUrl; }
    public String getSiteUrl() { return siteUrl; }
    public String getLanguage() { return language; }
    public NewsTopic getDefaultTopic() { return defaultTopic; }
    public boolean isEnabled() { return enabled; }
    public Instant getLastFetchedAt() { return lastFetchedAt; }
    public String getLastStatus() { return lastStatus; }
    public String getLastError() { return lastError; }
    public Integer getLastNewCount() { return lastNewCount; }

    void setEnabled(boolean enabled) { this.enabled = enabled; }
}
