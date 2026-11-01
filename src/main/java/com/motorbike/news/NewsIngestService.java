package com.motorbike.news;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.motorbike.config.AppProperties;

/**
 * L'"agente" delle notizie: legge tutti i feed attivi, classifica e salva gli articoli nuovi,
 * poi avvisa chi segue i relativi topic. Eseguito da {@link NewsScheduler} o manualmente dall'admin.
 */
@Service
public class NewsIngestService {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestService.class);

    public record RunResult(int sources, int failedSources, int newArticles, int notifiedArticles) {
    }

    private final NewsSourceRepository sources;
    private final NewsTopicRepository topics;
    private final FeedClient feedClient;
    private final ArticleClassifier classifier;
    private final NewsNotifier notifier;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NewsIngestService(NewsSourceRepository sources, NewsTopicRepository topics, FeedClient feedClient,
                             ArticleClassifier classifier, NewsNotifier notifier, JdbcClient jdbc,
                             TransactionTemplate tx, AppProperties props) {
        this.sources = sources;
        this.topics = topics;
        this.feedClient = feedClient;
        this.classifier = classifier;
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Avvio manuale dall'area admin, senza bloccare la richiesta HTTP. */
    @Async
    public void fetchAllAsync() {
        fetchAll();
    }

    /** @return esito dell'esecuzione, oppure null se un'altra esecuzione è già in corso */
    public RunResult fetchAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Import notizie già in corso, salto");
            return null;
        }
        try {
            return doFetchAll();
        } finally {
            running.set(false);
        }
    }

    private RunResult doFetchAll() {
        List<NewsSource> enabled = tx.execute(s -> sources.findEnabledWithTopic());
        List<NewsTopic> allTopics = tx.execute(s -> topics.findAllByOrderBySortOrder());
        Instant notifyAfter = Instant.now().minus(props.news().notifyMaxAge());

        List<NewArticle> toNotify = new ArrayList<>();
        int failed = 0;
        int inserted = 0;
        for (NewsSource source : enabled) {
            boolean firstImport = source.getLastFetchedAt() == null;
            try {
                List<FeedItem> items = feedClient.fetch(source.getFeedUrl());
                List<NewArticle> fresh = tx.execute(s -> store(source, items, allTopics));
                inserted += fresh.size();
                // primo import di una fonte: nessuna notifica (sarebbero decine di articoli "vecchi")
                if (!firstImport) {
                    fresh.stream().filter(a -> a.publishedAt().isAfter(notifyAfter)).forEach(toNotify::add);
                }
                log.info("Feed '{}': {} elementi, {} nuovi", source.getName(), items.size(), fresh.size());
            } catch (Exception e) {
                failed++;
                log.warn("Feed '{}' non letto: {}", source.getName(), e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                tx.executeWithoutResult(s -> sources.findById(source.getId())
                    .ifPresent(src -> src.recordFailure(Instant.now(), e.getMessage())));
            }
        }
        notifier.notifySubscribers(toNotify);
        log.info("Import notizie completato: {} fonti ({} con errori), {} articoli nuovi, {} notificati",
            enabled.size(), failed, inserted, toNotify.size());
        return new RunResult(enabled.size(), failed, inserted, toNotify.size());
    }

    /** Inserisce gli articoli non ancora presenti (per URL) e aggiorna lo stato della fonte. */
    private List<NewArticle> store(NewsSource source, List<FeedItem> items, List<NewsTopic> allTopics) {
        List<NewArticle> fresh = new ArrayList<>();
        for (FeedItem item : items) {
            NewsTopic topic = classifier.classify(item, source.getDefaultTopic(), allTopics);
            jdbc.sql("""
                    insert into news_articles (source_id, topic_id, title, url, summary, image_url, published_at, fetched_at)
                    values (:source, :topic, :title, :url, :summary, :image, :published, now())
                    on conflict (url) do nothing
                    returning id
                    """)
                .param("source", source.getId())
                .param("topic", topic.getId())
                .param("title", item.title())
                .param("url", item.url())
                .param("summary", item.summary().isEmpty() ? null : item.summary())
                .param("image", item.imageUrl())
                .param("published", java.sql.Timestamp.from(item.publishedAt()))
                .query(Long.class)
                .optional()
                .ifPresent(id -> fresh.add(new NewArticle(id, topic.getId(), item.title(), item.url(),
                    source.getName(), item.publishedAt())));
        }
        sources.findById(source.getId()).ifPresent(src -> src.recordSuccess(Instant.now(), fresh.size()));
        return fresh;
    }
}
