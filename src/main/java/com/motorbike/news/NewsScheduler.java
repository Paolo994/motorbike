package com.motorbike.news;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Esegue periodicamente l'import delle notizie (app.news.fetch-interval, default 30 minuti).
 * Disattivabile con app.news.fetch-enabled=false (es. nei test).
 * Con più istanze dell'applicazione servirà un lock distribuito (es. ShedLock) per non eseguirlo in parallelo.
 */
@Component
@ConditionalOnProperty(name = "app.news.fetch-enabled", havingValue = "true")
class NewsScheduler {

    private final NewsIngestService ingest;

    NewsScheduler(NewsIngestService ingest) {
        this.ingest = ingest;
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${app.news.fetch-interval}")
    void run() {
        ingest.fetchAll();
    }
}
