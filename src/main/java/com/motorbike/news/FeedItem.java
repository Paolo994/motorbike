package com.motorbike.news;

import java.time.Instant;

/** Elemento di un feed già normalizzato: testo semplice, URL http(s) validati. */
public record FeedItem(String title, String url, String summary, String imageUrl, Instant publishedAt) {
}
