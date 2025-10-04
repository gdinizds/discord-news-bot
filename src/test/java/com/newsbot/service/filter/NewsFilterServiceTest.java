package com.newsbot.service.filter;

import com.newsbot.model.NewsArticle;
import com.newsbot.repository.NewsArticleRepository;
import com.newsbot.service.duplicate.ContentDuplicateDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsFilterServiceTest {

    @Mock
    private NewsArticleRepository newsRepository;

    @Mock
    private ContentDuplicateDetector duplicateDetectionService;

    @InjectMocks
    private NewsFilterService newsFilterService;

    @Test
    void filterDuplicates_shouldFilterArticleWithDuplicateUrl() {
        NewsArticle article = createTestArticle();
        when(newsRepository.findByUrl(article.getUrl())).thenReturn(Mono.just(article));

        Mono<NewsArticle> result = newsFilterService.filterDuplicates(article);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void filterDuplicates_shouldFilterArticleWithDuplicateContentHash() {
        NewsArticle article = createTestArticle();
        when(newsRepository.findByUrl(article.getUrl())).thenReturn(Mono.empty());
        when(newsRepository.findByContentHash(article.getContentHash())).thenReturn(Mono.just(article));

        Mono<NewsArticle> result = newsFilterService.filterDuplicates(article);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void filterDuplicates_shouldFilterArticleWithSimilarContent() {
        NewsArticle article = createTestArticle();
        NewsArticle existingArticle = createTestArticle();
        existingArticle.setId(2L);
        existingArticle.setUrl("https://example.com/different");

        when(newsRepository.findByUrl(article.getUrl())).thenReturn(Mono.empty());
        when(newsRepository.findByContentHash(article.getContentHash())).thenReturn(Mono.empty());
        when(newsRepository.findRecentArticles(any(LocalDateTime.class))).thenReturn(Flux.just(existingArticle));
        when(duplicateDetectionService.areContentsSimilar(anyString(), anyString())).thenReturn(true);

        Mono<NewsArticle> result = newsFilterService.filterDuplicates(article);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void filterDuplicates_shouldNotFilterUniqueArticle() {
        NewsArticle article = createTestArticle();
        NewsArticle existingArticle = createTestArticle();
        existingArticle.setId(2L);
        existingArticle.setUrl("https://example.com/different");

        when(newsRepository.findByUrl(article.getUrl())).thenReturn(Mono.empty());
        when(newsRepository.findByContentHash(article.getContentHash())).thenReturn(Mono.empty());
        when(newsRepository.findRecentArticles(any(LocalDateTime.class))).thenReturn(Flux.just(existingArticle));
        when(duplicateDetectionService.areContentsSimilar(anyString(), anyString())).thenReturn(false);

        Mono<NewsArticle> result = newsFilterService.filterDuplicates(article);

        StepVerifier.create(result)
                .expectNext(article)
                .verifyComplete();
    }

    @Test
    void filterDuplicates_shouldHandleErrorsGracefully() {
        NewsArticle article = createTestArticle();
        when(newsRepository.findByUrl(article.getUrl())).thenReturn(Mono.error(new RuntimeException("Database error")));

        Mono<NewsArticle> result = newsFilterService.filterDuplicates(article);

        StepVerifier.create(result)
                .expectNext(article)
                .verifyComplete();
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