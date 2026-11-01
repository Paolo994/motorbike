package com.motorbike.news;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "news_topics")
public class NewsTopic {

    /** Topic di ripiego per gli articoli che non corrispondono a nessuna parola chiave. */
    public static final String FALLBACK_SLUG = "dal-mondo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String slug;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 40)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 4000)
    private String keywords;

    protected NewsTopic() {
    }

    /** Per i test del classificatore. */
    NewsTopic(Long id, String slug, String name, int sortOrder, String keywords) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.icon = "bi-newspaper";
        this.sortOrder = sortOrder;
        this.keywords = keywords;
    }

    public List<String> keywordList() {
        return Arrays.stream(keywords.split(","))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public int getSortOrder() { return sortOrder; }
    public String getKeywords() { return keywords; }

    void setKeywords(String keywords) { this.keywords = keywords; }
}
