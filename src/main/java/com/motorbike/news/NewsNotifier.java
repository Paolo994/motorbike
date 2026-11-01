package com.motorbike.news;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.motorbike.mail.MailService;
import com.motorbike.notification.NotificationService;
import com.motorbike.notification.NotificationType;
import com.motorbike.user.User;
import com.motorbike.user.UserRepository;

/**
 * Avvisa chi segue i topic degli articoli nuovi. Per ogni esecuzione dell'agente, ogni utente riceve
 * al massimo una notifica in-app per topic e una sola email riepilogativa (solo per i topic con email attiva).
 */
@Component
public class NewsNotifier {

    static final int MAX_ARTICLES_PER_SECTION = 5;

    /** Sezione dell'email riepilogativa. */
    public record DigestSection(String topicName, String topicSlug, List<NewArticle> articles, int total) {
    }

    private final TopicSubscriptionRepository subscriptions;
    private final NewsTopicRepository topics;
    private final UserRepository users;
    private final NotificationService notifications;
    private final MailService mail;

    NewsNotifier(TopicSubscriptionRepository subscriptions, NewsTopicRepository topics, UserRepository users,
                 NotificationService notifications, MailService mail) {
        this.subscriptions = subscriptions;
        this.topics = topics;
        this.users = users;
        this.notifications = notifications;
        this.mail = mail;
    }

    void notifySubscribers(List<NewArticle> articles) {
        if (articles.isEmpty()) {
            return;
        }
        Map<Long, List<NewArticle>> byTopic = articles.stream()
            .sorted(Comparator.comparing(NewArticle::publishedAt).reversed())
            .collect(Collectors.groupingBy(NewArticle::topicId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, NewsTopic> topicById = topics.findAllById(byTopic.keySet()).stream()
            .collect(Collectors.toMap(NewsTopic::getId, Function.identity()));

        Map<Long, List<TopicSubscription>> byUser = subscriptions.findByTopicIdIn(new ArrayList<>(byTopic.keySet()))
            .stream().collect(Collectors.groupingBy(TopicSubscription::getUserId));
        if (byUser.isEmpty()) {
            return;
        }
        for (User user : users.findAllById(byUser.keySet())) {
            if (!user.isEnabled()) {
                continue;
            }
            List<DigestSection> emailSections = new ArrayList<>();
            List<TopicSubscription> userSubs = byUser.get(user.getId()).stream()
                .sorted(Comparator.comparing(s -> topicById.get(s.getTopicId()).getSortOrder()))
                .toList();
            for (TopicSubscription sub : userSubs) {
                NewsTopic topic = topicById.get(sub.getTopicId());
                List<NewArticle> list = byTopic.get(sub.getTopicId());
                String title = list.size() == 1
                    ? "Nuovo articolo: " + topic.getName()
                    : list.size() + " nuovi articoli: " + topic.getName();
                notifications.notify(user, NotificationType.NEWS, title, list.getFirst().title(),
                    "/news?topic=" + topic.getSlug());
                if (sub.isEmailEnabled()) {
                    emailSections.add(new DigestSection(topic.getName(), topic.getSlug(),
                        list.subList(0, Math.min(MAX_ARTICLES_PER_SECTION, list.size())), list.size()));
                }
            }
            if (!emailSections.isEmpty()) {
                int total = emailSections.stream().mapToInt(DigestSection::total).sum();
                String subject = total == 1 ? "MotoRide · 1 nuova notizia per te" : "MotoRide · " + total + " nuove notizie per te";
                mail.send(user.getEmail(), subject, "email/news-digest",
                    Map.of("username", user.getUsername(), "sections", emailSections, "total", total));
            }
        }
    }
}
