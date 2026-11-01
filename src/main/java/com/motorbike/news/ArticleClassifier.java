package com.motorbike.news;

import java.util.List;

/**
 * Assegna un articolo a uno dei topic. Implementazione attuale: {@link KeywordArticleClassifier}.
 * In Fase 2 si potrà aggiungere un'implementazione basata su LLM (es. Claude Haiku) con questa stessa interfaccia.
 */
public interface ArticleClassifier {

    /**
     * @param item         articolo da classificare
     * @param defaultTopic topic predefinito della fonte (può essere null)
     * @param topics       tutti i topic, in ordine di priorità
     * @return il topic scelto (mai null)
     */
    NewsTopic classify(FeedItem item, NewsTopic defaultTopic, List<NewsTopic> topics);
}
