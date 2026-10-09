package com.lcblog.article;

import java.time.LocalDateTime;
import java.util.List;

public class Article {
    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    public Long id;
    public String title;
    public String slug;
    public String summary;
    public String contentMd;
    public Long categoryId;
    public String categoryName;
    public String categorySlug;
    public String status;
    public boolean pinned;
    public Long coverMediaId;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime publishedAt;
    public List<Tag> tags = List.of();

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }
}
