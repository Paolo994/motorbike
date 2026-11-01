package com.motorbike.news;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Classificazione per parole chiave: ogni parola chiave di un topic trovata nel titolo vale 3 punti,
 * nel riassunto 1 punto. Vince il punteggio più alto; il topic predefinito della fonte riceve +1
 * (spareggio a suo favore) e, a parità, prevale l'ordine dei topic (sort_order).
 * Se nessuna parola chiave corrisponde: topic predefinito della fonte, altrimenti "Dal mondo".
 */
@Component
public class KeywordArticleClassifier implements ArticleClassifier {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    /** Cache delle regex compilate, indicizzata per elenco di parole chiave (cambia se l'admin le modifica). */
    private final Map<String, List<Pattern>> patterns = new ConcurrentHashMap<>();

    @Override
    public NewsTopic classify(FeedItem item, NewsTopic defaultTopic, List<NewsTopic> topics) {
        String title = normalize(item.title());
        String summary = normalize(item.summary());

        NewsTopic best = null;
        int bestScore = 0;
        for (NewsTopic topic : topics) {                       // topics già ordinati per priorità
            int score = 0;
            for (Pattern p : patternsFor(topic)) {
                if (p.matcher(title).find()) {
                    score += 3;
                }
                if (p.matcher(summary).find()) {
                    score += 1;
                }
            }
            if (score > 0 && defaultTopic != null && topic.getId().equals(defaultTopic.getId())) {
                score += 1;
            }
            if (score > bestScore) {
                best = topic;
                bestScore = score;
            }
        }
        if (best != null) {
            return best;
        }
        if (defaultTopic != null) {
            return defaultTopic;
        }
        return topics.stream().filter(t -> NewsTopic.FALLBACK_SLUG.equals(t.getSlug())).findFirst()
            .orElse(topics.getLast());
    }

    private List<Pattern> patternsFor(NewsTopic topic) {
        return patterns.computeIfAbsent(topic.getKeywords(), k -> topic.keywordList().stream()
            .map(KeywordArticleClassifier::normalize)
            .filter(s -> !s.isEmpty())
            // parola intera: niente lettere/cifre subito prima o dopo ("gp" non corrisponde a "gpone")
            .map(kw -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(kw) + "(?![\\p{L}\\p{N}])"))
            .toList());
    }

    /** minuscolo e senza accenti: "Márquez" → "marquez", "novità" → "novita". */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("");
    }
}
