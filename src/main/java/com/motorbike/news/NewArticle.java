package com.motorbike.news;

import java.time.Instant;

/** Articolo appena inserito, con i dati necessari per notifiche ed email. */
public record NewArticle(long id, long topicId, String title, String url, String sourceName, Instant publishedAt) {
}
