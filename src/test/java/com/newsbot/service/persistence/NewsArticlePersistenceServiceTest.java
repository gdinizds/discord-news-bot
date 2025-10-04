package com.newsbot.service.persistence;

import com.newsbot.model.NewsArticle;
import com.newsbot.repository.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsArticlePersistenceServiceTest {

    @Mock
    private NewsArticleRepository newsRepository;

    @InjectMocks
    private NewsArticlePersistenceService newsArticlePersistenceService;

    @Test
    void saveArticle_shouldSaveAndReturnArticle() {
        NewsArticle article = createTestArticle();
        when(newsRepository.save(any(NewsArticle.class))).thenReturn(Mono.just(article));

        Mono<NewsArticle> result = newsArticlePersistenceService.saveArticle(article);

        StepVerifier.create(result)
                .expectNext(article)
                .verifyComplete();

        verify(newsRepository, times(1)).save(article);
    }

    @Test
    void saveArticle_shouldHandleError() {
        NewsArticle article = createTestArticle();
        RuntimeException exception = new RuntimeException("Database error");
        when(newsRepository.save(any(NewsArticle.class))).thenReturn(Mono.error(exception));

        Mono<NewsArticle> result = newsArticlePersistenceService.saveArticle(article);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable.equals(exception))
                .verify();

        verify(newsRepository, times(1)).save(article);
    }

    @Test
    void markArticlesAsSent_shouldUpdateAndSaveArticles() {
        NewsArticle article1 = createTestArticle();
        NewsArticle article2 = createTestArticle();
        List<NewsArticle> articles = Arrays.asList(article1, article2);

        NewsArticle savedArticle1 = createTestArticle();
        savedArticle1.setSentToDiscord(true);
        NewsArticle savedArticle2 = createTestArticle();
        savedArticle2.setSentToDiscord(true);

        when(newsRepository.save(article1)).thenReturn(Mono.just(savedArticle1));
        when(newsRepository.save(article2)).thenReturn(Mono.just(savedArticle2));

        Mono<Void> result = newsArticlePersistenceService.markArticlesAsSent(articles);

        StepVerifier.create(result)
                .verifyComplete();

        verify(newsRepository, times(1)).save(article1);
        verify(newsRepository, times(1)).save(article2);
        
        assertTrue(article1.getSentToDiscord());
        assertTrue(article2.getSentToDiscord());
    }

    @Test
    void markArticlesAsSent_shouldHandleEmptyList() {
        List<NewsArticle> articles = List.of();

        Mono<Void> result = newsArticlePersistenceService.markArticlesAsSent(articles);

        StepVerifier.create(result)
                .verifyComplete();

        verify(newsRepository, never()).save(any());
    }

    private NewsArticle createTestArticle() {
        return NewsArticle.builder()
                .id(1L)
                .title("Test Article")
                .description("This is a test article")
                .url("https://example.com/test")
                .contentHash("abcdef123456")
                .source("Test Source")
                .publishedDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .sentToDiscord(false)
                .build();
    }
}