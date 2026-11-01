package com.motorbike.news;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    @EntityGraph(attributePaths = {"source", "topic"})
    @Query("select a from NewsArticle a order by a.publishedAt desc, a.id desc")
    Page<NewsArticle> findLatest(Pageable page);

    @EntityGraph(attributePaths = {"source", "topic"})
    @Query("select a from NewsArticle a where a.topic.id = :topicId order by a.publishedAt desc, a.id desc")
    Page<NewsArticle> findLatestByTopic(Long topicId, Pageable page);

    @Query("select a.source.id, count(a) from NewsArticle a group by a.source.id")
    List<Object[]> countBySource();
}
