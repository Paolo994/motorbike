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

/** Articolo importato da un feed: titolo, breve riassunto e link alla fonte (il contenuto resta sul sito originale). */
@Entity
@Table(name = "news_articles")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id")
    private NewsSource source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id")
    private NewsTopic topic;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(length = 600)
    private String summary;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected NewsArticle() {
    }

    public Long getId() { return id; }
    public NewsSource getSource() { return source; }
    public NewsTopic getTopic() { return topic; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSummary() { return summary; }
    public String getImageUrl() { return imageUrl; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getFetchedAt() { return fetchedAt; }
}
