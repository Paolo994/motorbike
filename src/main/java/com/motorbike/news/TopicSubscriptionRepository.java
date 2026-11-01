package com.motorbike.news;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicSubscriptionRepository extends JpaRepository<TopicSubscription, Long> {

    List<TopicSubscription> findByUserId(Long userId);

    Optional<TopicSubscription> findByUserIdAndTopicId(Long userId, Long topicId);

    List<TopicSubscription> findByTopicIdIn(List<Long> topicIds);
}
