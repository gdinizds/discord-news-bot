package com.newsbot.repository;

import com.newsbot.model.NewsArticle;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface NewsArticleRepository extends R2dbcRepository<NewsArticle, Long> {
    @Query("SELECT * FROM news_articles WHERE url = :url and sent_to_discord = true ORDER BY created_at DESC")
    Mono<NewsArticle> findByUrl(String url);

    @Query("SELECT * FROM news_articles WHERE content_hash = :contentHash and sent_to_discord = true ORDER BY created_at DESC")
    Mono<NewsArticle> findByContentHash(String contentHash);

    @Query("SELECT * FROM news_articles WHERE created_at >= :since and sent_to_discord = true ORDER BY created_at DESC")
    Flux<NewsArticle> findRecentArticles(LocalDateTime since);

}
