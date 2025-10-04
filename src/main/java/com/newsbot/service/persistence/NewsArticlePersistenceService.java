package com.newsbot.service.persistence;

import com.newsbot.model.NewsArticle;
import com.newsbot.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class NewsArticlePersistenceService {

    private final NewsArticleRepository newsRepository;

    public Mono<NewsArticle> saveArticle(NewsArticle article) {
        return newsRepository.save(article)
                .doOnSuccess(saved -> log.debug("Article saved: {}", saved.getTitle()))
                .doOnError(error -> log.error("Error saving article '{}': {}",
                        article.getTitle(), error.getMessage()));
    }

    public Mono<Void> markArticlesAsSent(List<NewsArticle> articles) {
        return Flux.fromIterable(articles)
                .map(article -> {
                    article.setSentToDiscord(true);
                    return article;
                })
                .flatMap(newsRepository::save)
                .then()
                .doOnSuccess(v -> log.info("Marked {} articles as sent", articles.size()))
                .doOnError(error -> log.error("Error marking articles as sent: {}", error.getMessage()));
    }
}