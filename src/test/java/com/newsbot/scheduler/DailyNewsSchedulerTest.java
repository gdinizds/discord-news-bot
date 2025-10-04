package com.newsbot.scheduler;

import com.newsbot.model.NewsArticle;
import com.newsbot.service.persistence.NewsArticlePersistenceService;
import com.newsbot.service.news.NewsEditorService;
import com.newsbot.service.news.RssNewsService;
import com.newsbot.service.discord.NewsDiscordBatchProcessor;
import com.newsbot.service.filter.NewsFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class DailyNewsSchedulerTest {

    @Mock
    private RssNewsService rssNewsService;

    @Mock
    private NewsFilterService newsFilterService;

    @Mock
    private NewsArticlePersistenceService newsArticlePersistenceService;

    @Mock
    private NewsEditorService newsEditorService;

    @Mock
    private NewsDiscordBatchProcessor discordBatchProcessor;

    @InjectMocks
    private DailyNewsScheduler dailyNewsScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dailyNewsScheduler, "webhookUrl", "https://discord.webhook.url");
    }

    @Test
    void executeManually_shouldProcessAndSendNews() {
        NewsArticle article1 = createTestArticle(1L);
        NewsArticle article2 = createTestArticle(2L);
        List<NewsArticle> articles = List.of(article1, article2);

        when(rssNewsService.fetchAllNews()).thenReturn(Flux.just(article1, article2));
        when(newsFilterService.filterDuplicates(any(NewsArticle.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(newsArticlePersistenceService.saveArticle(any(NewsArticle.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(newsEditorService.selectTopNews(anyList())).thenReturn(Mono.just(articles));
        when(discordBatchProcessor.processAndSendToDiscord(anyList(), anyString())).thenReturn(Mono.just(articles));
        when(newsArticlePersistenceService.markArticlesAsSent(anyList())).thenReturn(Mono.empty());

        Mono<Integer> result = dailyNewsScheduler.executeManually();

        StepVerifier.create(result)
                .expectNext(2)
                .verifyComplete();

        verify(rssNewsService, times(1)).fetchAllNews();
        verify(newsFilterService, times(2)).filterDuplicates(any(NewsArticle.class));
        verify(newsArticlePersistenceService, times(2)).saveArticle(any(NewsArticle.class));
        verify(newsEditorService, times(1)).selectTopNews(anyList());
        verify(discordBatchProcessor, times(1)).processAndSendToDiscord(anyList(), anyString());
        verify(newsArticlePersistenceService, times(1)).markArticlesAsSent(anyList());
    }

    @Test
    void executeManually_shouldHandleEmptyArticleList() {
        when(rssNewsService.fetchAllNews()).thenReturn(Flux.empty());

        Mono<Integer> result = dailyNewsScheduler.executeManually();

        StepVerifier.create(result)
                .expectNext(0)
                .verifyComplete();

        verify(rssNewsService, times(1)).fetchAllNews();
        verify(newsEditorService, never()).selectTopNews(anyList());
        verify(discordBatchProcessor, never()).processAndSendToDiscord(anyList(), anyString());
        verify(newsArticlePersistenceService, never()).markArticlesAsSent(anyList());
    }

    @Test
    void executeManually_shouldHandleNoSelectedArticles() {
        NewsArticle article1 = createTestArticle(1L);
        NewsArticle article2 = createTestArticle(2L);

        when(rssNewsService.fetchAllNews()).thenReturn(Flux.just(article1, article2));
        when(newsFilterService.filterDuplicates(any(NewsArticle.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(newsArticlePersistenceService.saveArticle(any(NewsArticle.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(newsEditorService.selectTopNews(anyList())).thenReturn(Mono.just(List.of()));

        Mono<Integer> result = dailyNewsScheduler.executeManually();

        StepVerifier.create(result)
                .expectNext(0)
                .verifyComplete();

        verify(rssNewsService, times(1)).fetchAllNews();
        verify(newsFilterService, times(2)).filterDuplicates(any(NewsArticle.class));
        verify(newsArticlePersistenceService, times(2)).saveArticle(any(NewsArticle.class));
        verify(newsEditorService, times(1)).selectTopNews(anyList());
        verify(discordBatchProcessor, never()).processAndSendToDiscord(anyList(), anyString());
        verify(newsArticlePersistenceService, never()).markArticlesAsSent(anyList());
    }

    @Test
    void executeManually_shouldHandleErrorInRssService() {
        when(rssNewsService.fetchAllNews()).thenReturn(Flux.error(new RuntimeException("Test error")));

        Mono<Integer> result = dailyNewsScheduler.executeManually();

        StepVerifier.create(result)
                .expectNext(-1)
                .verifyComplete();
    }

    @Test
    void executeManually_shouldOnlyMarkSuccessfullySentArticlesAsSent() {
        NewsArticle article1 = createTestArticle(1L);
        NewsArticle article2 = createTestArticle(2L);
        NewsArticle article3 = createTestArticle(3L);
        List<NewsArticle> allArticles = List.of(article1, article2, article3);
        List<NewsArticle> successfulArticles = List.of(article1, article3);

        when(rssNewsService.fetchAllNews()).thenReturn(Flux.fromIterable(allArticles));
        when(newsFilterService.filterDuplicates(any(NewsArticle.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(newsArticlePersistenceService.saveArticle(any(NewsArticle.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(newsEditorService.selectTopNews(anyList())).thenReturn(Mono.just(allArticles));
        when(discordBatchProcessor.processAndSendToDiscord(anyList(), anyString())).thenReturn(Mono.just(successfulArticles));
        when(newsArticlePersistenceService.markArticlesAsSent(anyList())).thenReturn(Mono.empty());

        Mono<Integer> result = dailyNewsScheduler.executeManually();

        StepVerifier.create(result)
                .expectNext(3)
                .verifyComplete();

        verify(newsArticlePersistenceService).markArticlesAsSent(successfulArticles);
    }

    private NewsArticle createTestArticle(Long id) {
        return NewsArticle.builder()
                .id(id)
                .title("Test Article " + id)
                .description("This is test article " + id)
                .url("https://example.com/test" + id)
                .contentHash("abcdef" + id)
                .source("Test Source")
                .publishedDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .sentToDiscord(false)
                .build();
    }
}
