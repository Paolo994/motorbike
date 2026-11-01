package com.motorbike.news;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {

    @Query("select s from NewsSource s left join fetch s.defaultTopic where s.enabled = true order by s.id")
    List<NewsSource> findEnabledWithTopic();

    @Query("select s from NewsSource s left join fetch s.defaultTopic order by s.name")
    List<NewsSource> findAllWithTopic();

    boolean existsByFeedUrl(String feedUrl);
}
