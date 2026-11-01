package com.motorbike.news;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jdom2.Element;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.motorbike.config.AppProperties;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

/** Scarica e interpreta un feed RSS/Atom, restituendo elementi normalizzati. */
@Component
public class FeedClient {

    static final int MAX_TITLE = 300;
    static final int MAX_SUMMARY = 400;
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final String userAgent;

    public FeedClient(AppProperties props) {
        this.userAgent = props.http().userAgent();
    }

    public List<FeedItem> fetch(String feedUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", userAgent)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.5")
            .GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        if (response.body().length > MAX_BYTES) {
            throw new IOException("Feed troppo grande");
        }
        return parse(response.body());
    }

    /** Separato da {@link #fetch} per poterlo testare senza rete. */
    List<FeedItem> parse(byte[] xml) throws IOException {
        SyndFeed feed;
        try {
            // SyndFeedInput di default non consente DOCTYPE: protegge da XXE
            feed = new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            throw new IOException("Feed non valido: " + e.getMessage(), e);
        }
        List<FeedItem> items = new ArrayList<>();
        for (SyndEntry entry : feed.getEntries()) {
            String url = normalizeUrl(entry.getLink() != null ? entry.getLink() : entry.getUri());
            String title = truncate(text(entry.getTitle()), MAX_TITLE);
            if (url == null || title.isEmpty()) {
                continue;
            }
            String html = htmlOf(entry);
            items.add(new FeedItem(title, url, truncate(text(html), MAX_SUMMARY), imageOf(entry, html, url),
                dateOf(entry)));
        }
        return items;
    }

    // ---------------------------------------------------------------- estrazione campi

    private static String htmlOf(SyndEntry entry) {
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            return entry.getDescription().getValue();
        }
        return entry.getContents().stream().map(SyndContent::getValue)
            .filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
    }

    private static Instant dateOf(SyndEntry entry) {
        Date d = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
        Instant now = Instant.now();
        if (d == null || d.toInstant().isAfter(now)) {
            return now;                                     // date mancanti o nel futuro (fusi orari errati)
        }
        return d.toInstant();
    }

    private static String imageOf(SyndEntry entry, String html, String articleUrl) {
        for (SyndEnclosure enc : entry.getEnclosures()) {
            if (enc.getType() != null && enc.getType().startsWith("image/")) {
                String u = httpUrl(enc.getUrl());
                if (u != null) {
                    return u;
                }
            }
        }
        // media:content / media:thumbnail (namespace Media RSS, non gestito nativamente da Rome)
        for (Element el : entry.getForeignMarkup()) {
            if (("content".equals(el.getName()) || "thumbnail".equals(el.getName()))
                && el.getNamespaceURI().contains("search.yahoo.com/mrss")) {
                String medium = el.getAttributeValue("medium");
                String type = el.getAttributeValue("type");
                boolean image = medium == null || "image".equals(medium) || (type != null && type.startsWith("image/"));
                String u = httpUrl(el.getAttributeValue("url"));
                if (image && u != null) {
                    return u;
                }
            }
        }
        if (!html.isBlank()) {
            Document doc = Jsoup.parse(html, articleUrl);
            var img = doc.selectFirst("img[src]");
            if (img != null) {
                return httpUrl(img.absUrl("src"));
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- helper

    /** HTML → testo semplice, spazi compattati. */
    static String text(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.parse(html).text().replaceAll("\\s+", " ").strip();
    }

    static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int cut = s.lastIndexOf(' ', max - 1);
        return s.substring(0, cut > max / 2 ? cut : max - 1) + "…";
    }

    /** Solo http/https, senza frammento e senza parametri di tracciamento utm_*. */
    static String normalizeUrl(String raw) {
        String url = httpUrl(raw);
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery() == null ? null : java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .filter(p -> !p.toLowerCase(Locale.ROOT).startsWith("utm_"))
                .collect(Collectors.joining("&"));
            String rebuilt = uri.getScheme() + "://" + uri.getRawAuthority()
                + (uri.getRawPath() == null ? "" : uri.getRawPath())
                + (query == null || query.isEmpty() ? "" : "?" + query);
            return rebuilt.length() <= 1000 ? rebuilt : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String httpUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        String lower = s.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://")) || s.length() > 1000) {
            return null;
        }
        try {
            URI.create(s);
            return s;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
