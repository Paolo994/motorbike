package com.motorbike.news;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lettura delle notizie e gestione dei topic seguiti dall'utente. */
@Service
public class NewsService {

    public static final int PAGE_SIZE = 20;

    private final NewsTopicRepository topics;
    private final NewsArticleRepository articles;
    private final TopicSubscriptionRepository subscriptions;

    public NewsService(NewsTopicRepository topics, NewsArticleRepository articles,
                       TopicSubscriptionRepository subscriptions) {
        this.topics = topics;
        this.articles = articles;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public List<NewsTopic> topics() {
        return topics.findAllByOrderBySortOrder();
    }

    @Transactional(readOnly = true)
    public Optional<NewsTopic> topic(String slug) {
        return slug == null || slug.isBlank() ? Optional.empty() : topics.findBySlug(slug);
    }

    /** Articoli più recenti, tutti o di un topic. */
    @Transactional(readOnly = true)
    public Page<NewsArticle> latest(NewsTopic topic, int page) {
        PageRequest request = PageRequest.of(Math.max(0, page), PAGE_SIZE);
        return topic == null ? articles.findLatest(request) : articles.findLatestByTopic(topic.getId(), request);
    }

    /** topicId → iscrizione dell'utente. */
    @Transactional(readOnly = true)
    public Map<Long, TopicSubscription> subscriptions(Long userId) {
        return subscriptions.findByUserId(userId).stream()
            .collect(Collectors.toMap(TopicSubscription::getTopicId, Function.identity()));
    }

    @Transactional
    public void follow(Long userId, String slug, boolean email) {
        NewsTopic topic = topics.findBySlug(slug).orElseThrow(() -> new TopicNotFoundException(slug));
        subscriptions.findByUserIdAndTopicId(userId, topic.getId())
            .ifPresentOrElse(s -> s.setEmailEnabled(email),
                () -> subscriptions.save(new TopicSubscription(userId, topic.getId(), email)));
    }

    @Transactional
    public void unfollow(Long userId, String slug) {
        NewsTopic topic = topics.findBySlug(slug).orElseThrow(() -> new TopicNotFoundException(slug));
        subscriptions.findByUserIdAndTopicId(userId, topic.getId()).ifPresent(subscriptions::delete);
    }
}
