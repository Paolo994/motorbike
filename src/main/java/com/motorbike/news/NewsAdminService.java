package com.motorbike.news;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestione di fonti e parole chiave dei topic dall'area admin. */
@Service
public class NewsAdminService {

    public record Stats(long sources, long enabledSources, long failingSources, long articles, Instant lastFetch) {
    }

    private final NewsSourceRepository sources;
    private final NewsTopicRepository topics;
    private final NewsArticleRepository articles;

    public NewsAdminService(NewsSourceRepository sources, NewsTopicRepository topics, NewsArticleRepository articles) {
        this.sources = sources;
        this.topics = topics;
        this.articles = articles;
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        List<NewsSource> all = sources.findAll();
        return new Stats(all.size(),
            all.stream().filter(NewsSource::isEnabled).count(),
            all.stream().filter(s -> s.isEnabled() && "ERROR".equals(s.getLastStatus())).count(),
            articles.count(),
            all.stream().map(NewsSource::getLastFetchedAt).filter(java.util.Objects::nonNull)
                .max(Instant::compareTo).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<NewsSource> sources() {
        return sources.findAllWithTopic();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> articleCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : articles.countBySource()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional
    public void addSource(String name, String feedUrl, String siteUrl, String language, String defaultTopicSlug) {
        String url = feedUrl == null ? "" : feedUrl.strip();
        String lower = url.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://")) || url.length() > 500) {
            throw new IllegalArgumentException("L'indirizzo del feed deve iniziare con http:// o https://");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Indica un nome per la fonte");
        }
        if (sources.existsByFeedUrl(url)) {
            throw new IllegalArgumentException("Questa fonte è già presente");
        }
        NewsTopic topic = defaultTopicSlug == null || defaultTopicSlug.isBlank() ? null
            : topics.findBySlug(defaultTopicSlug).orElse(null);
        String lang = "en".equals(language) ? "en" : "it";
        sources.save(new NewsSource(name.strip(), url, siteUrl == null || siteUrl.isBlank() ? null : siteUrl.strip(),
            lang, topic));
    }

    @Transactional
    public void setEnabled(Long sourceId, boolean enabled) {
        sources.findById(sourceId).ifPresent(s -> s.setEnabled(enabled));
    }

    @Transactional
    public void updateKeywords(String slug, String keywords) {
        NewsTopic topic = topics.findBySlug(slug).orElseThrow(() -> new TopicNotFoundException(slug));
        String cleaned = keywords == null ? "" : String.join(",", java.util.Arrays.stream(keywords.split("[,\\n]"))
            .map(String::strip).filter(s -> !s.isEmpty()).distinct().toList());
        if (cleaned.length() > 4000) {
            throw new IllegalArgumentException("Troppe parole chiave (massimo 4000 caratteri)");
        }
        topic.setKeywords(cleaned);
    }
}
