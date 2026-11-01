package com.motorbike.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.motorbike.config.AppProperties;

/** Parsing dei feed e classificazione, senza rete né database. */
class FeedParsingTest {

    private final FeedClient client = new FeedClient(new AppProperties(null, null, null, null,
        new AppProperties.Http("test"), null, null, null, null));

    private static final String RSS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>Test</title>
            <item>
              <title><![CDATA[MotoGP | Bagnaia vince a <b>Sepang</b>]]></title>
              <link>https://example.com/motogp/bagnaia?utm_source=rss&amp;id=7#comments</link>
              <description><![CDATA[<p>Gara <strong>perfetta</strong> del piemontese.</p><img src="/img/a.jpg">]]></description>
              <pubDate>Sun, 26 Oct 2025 14:00:00 GMT</pubDate>
            </item>
            <item>
              <title>Con immagine media</title>
              <link>https://example.com/b</link>
              <media:content url="https://cdn.example.com/b.jpg" medium="image"/>
            </item>
            <item>
              <title>Con enclosure</title>
              <link>https://example.com/c</link>
              <enclosure url="https://cdn.example.com/c.png" type="image/png" length="1"/>
              <pubDate>Sun, 26 Oct 2099 14:00:00 GMT</pubDate>
            </item>
            <item>
              <title>Link pericoloso</title>
              <link>javascript:alert(1)</link>
            </item>
            <item>
              <title></title>
              <link>https://example.com/senza-titolo</link>
            </item>
          </channel>
        </rss>
        """;

    @Test
    void parsesAndNormalizesItems() throws IOException {
        List<FeedItem> items = client.parse(RSS.getBytes(StandardCharsets.UTF_8));
        assertThat(items).hasSize(3);                           // scartati: link javascript:, titolo vuoto

        FeedItem first = items.get(0);
        assertThat(first.title()).isEqualTo("MotoGP | Bagnaia vince a Sepang");          // HTML rimosso
        assertThat(first.url()).isEqualTo("https://example.com/motogp/bagnaia?id=7");     // utm e frammento rimossi
        assertThat(first.summary()).isEqualTo("Gara perfetta del piemontese.");
        assertThat(first.imageUrl()).isEqualTo("https://example.com/img/a.jpg");          // URL relativo risolto
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2025-10-26T14:00:00Z"));

        assertThat(items.get(1).imageUrl()).isEqualTo("https://cdn.example.com/b.jpg");
        assertThat(items.get(2).imageUrl()).isEqualTo("https://cdn.example.com/c.png");
        assertThat(items.get(2).publishedAt()).isBefore(Instant.now().plusSeconds(5));     // data futura → adesso
    }

    /** GPOne English usa date ISO-8601 nel pubDate RSS: senza rome.properties diventerebbero "adesso". */
    @Test
    void parsesIsoDatesInRssPubDate() throws IOException {
        String rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>t</title>
              <item><title>ISO</title><link>https://e.com/iso</link><pubDate>2026-10-31T18:11:59+0100</pubDate></item>
            </channel></rss>
            """;
        FeedItem item = client.parse(rss.getBytes(StandardCharsets.UTF_8)).getFirst();
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-10-31T17:11:59Z"));
    }

    @Test
    void rejectsDoctypeToPreventXxe() {
        String xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss version="2.0"><channel><title>x</title><item><title>&xxe;</title><link>https://e.com/x</link></item></channel></rss>
            """;
        assertThatThrownBy(() -> client.parse(xxe.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IOException.class);
    }

    @Test
    void longSummariesAreTruncatedOnWordBoundary() {
        String s = FeedClient.truncate("parola ".repeat(100).strip(), 50);
        assertThat(s).hasSizeLessThanOrEqualTo(50).endsWith("…").doesNotContain("parol…");
    }

    // ---------------------------------------------------------------- classificatore

    private final NewsTopic motogp = new NewsTopic(1L, "motogp", "MotoGP", 10, "motogp,gp,bagnaia,marquez,sprint");
    private final NewsTopic sbk = new NewsTopic(2L, "superbike", "Superbike", 20, "superbike,sbk,bulega,razgatlioglu");
    private final NewsTopic minor = new NewsTopic(3L, "moto2-moto3", "Moto2 e Moto3", 30, "moto2,moto3");
    private final NewsTopic models = new NewsTopic(4L, "modelli", "Nuovi modelli", 40, "prova,novita,eicma,nuova");
    private final NewsTopic world = new NewsTopic(5L, "dal-mondo", "Dal mondo", 50, "mercato,vendite");
    private final List<NewsTopic> topics = List.of(motogp, sbk, minor, models, world);
    private final KeywordArticleClassifier classifier = new KeywordArticleClassifier();

    private FeedItem item(String title, String summary) {
        return new FeedItem(title, "https://e.com/" + title.hashCode(), summary, null, Instant.now());
    }

    @Test
    void classifiesByKeywordsWithTitleWeight() {
        assertThat(classifier.classify(item("Márquez in pole nella Sprint", ""), null, topics)).isEqualTo(motogp);
        // fonte MotoGP ma articolo sulla Superbike: vincono le parole chiave
        assertThat(classifier.classify(item("Bulega campione Superbike", "a Jerez"), motogp, topics)).isEqualTo(sbk);
        // accenti e maiuscole non contano: "NOVITÀ" corrisponde a "novita"
        assertThat(classifier.classify(item("Tutte le NOVITÀ di EICMA", ""), null, topics)).isEqualTo(models);
        assertThat(classifier.classify(item("Moto2: vittoria al debutto", "come in MotoGP"), null, topics)).isEqualTo(minor);
    }

    @Test
    void usesWholeWordsOnly() {
        // "gp" non deve corrispondere dentro "gpone": nessuna parola chiave → topic della fonte
        assertThat(classifier.classify(item("Intervista esclusiva su GPOne", ""), models, topics)).isEqualTo(models);
    }

    @Test
    void fallsBackToSourceTopicThenWorld() {
        assertThat(classifier.classify(item("Intervista esclusiva", "chiacchierata"), sbk, topics)).isEqualTo(sbk);
        assertThat(classifier.classify(item("Intervista esclusiva", "chiacchierata"), null, topics)).isEqualTo(world);
    }

    @Test
    void sourceTopicBreaksTies() {
        // una parola chiave per parte nel titolo: senza spareggio vincerebbe MotoGP (priorità più alta)
        FeedItem tie = item("Dalla MotoGP alla Superbike", "");
        assertThat(classifier.classify(tie, null, topics)).isEqualTo(motogp);
        assertThat(classifier.classify(tie, sbk, topics)).isEqualTo(sbk);
    }
}
