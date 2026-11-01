package com.motorbike.news;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsTopicRepository extends JpaRepository<NewsTopic, Long> {

    List<NewsTopic> findAllByOrderBySortOrder();

    Optional<NewsTopic> findBySlug(String slug);
}
