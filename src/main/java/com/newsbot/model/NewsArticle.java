package com.newsbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("news_articles")
public class NewsArticle {
    @Id
    private Long id;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("url")
    private String url;

    @Column("content_hash")
    private String contentHash;

    @Column("source")
    private String source;

    @Column("published_date")
    private LocalDateTime publishedDate;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("sent_to_discord")
    private Boolean sentToDiscord;
}
