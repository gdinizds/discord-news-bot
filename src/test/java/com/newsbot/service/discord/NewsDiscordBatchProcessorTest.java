package com.newsbot.service.discord;

import com.newsbot.dto.DiscordWebhookPayload;
import com.newsbot.model.NewsArticle;
import com.newsbot.service.translation.NewsTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsDiscordBatchProcessorTest {

    @Mock
    private NewsTranslationService newsTranslationService;

    @Mock
    private DiscordWebhookService discordService;

    @InjectMocks
    private NewsDiscordBatchProcessor batchProcessor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(batchProcessor, "embedColor", 3447003);
        ReflectionTestUtils.setField(batchProcessor, "maxEmbedsPerMessage", 2);
    }

    @Test
    void processAndSendToDiscord_shouldProcessAndSendArticles() {
        NewsArticle article1 = createTestArticle(1L);
        NewsArticle article2 = createTestArticle(2L);
        List<NewsArticle> articles = List.of(article1, article2);
        String webhookUrl = "https://discord.webhook.url";

        NewsTranslationService.ProcessedNews processedNews = NewsTranslationService.ProcessedNews.builder()
                .title("Translated Title")
                .description("Translated Description")
                .build();

        when(newsTranslationService.processSingleNews(anyString(), anyString()))
                .thenReturn(Mono.just(processedNews));
        when(discordService.sendEmbeds(anyString(), any(DiscordWebhookPayload.class)))
                .thenReturn(Mono.empty());

        Mono<List<NewsArticle>> result = batchProcessor.processAndSendToDiscord(articles, webhookUrl);

        StepVerifier.create(result)
                .expectNextMatches(sentArticles -> 
                    sentArticles.size() == 2 && 
                    sentArticles.contains(article1) && 
                    sentArticles.contains(article2))
                .verifyComplete();

        verify(newsTranslationService, times(2)).processSingleNews(anyString(), anyString());
        verify(discordService, times(1)).sendEmbeds(anyString(), any(DiscordWebhookPayload.class));
    }

    @Test
    void processAndSendToDiscord_shouldHandleEmptyArticleList() {
        List<NewsArticle> articles = List.of();
        String webhookUrl = "https://discord.webhook.url";

        Mono<List<NewsArticle>> result = batchProcessor.processAndSendToDiscord(articles, webhookUrl);

        StepVerifier.create(result)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

        verifyNoInteractions(newsTranslationService);
        verifyNoInteractions(discordService);
    }

    @Test
    void processAndSendToDiscord_shouldHandleTranslationErrors() {
        NewsArticle article1 = createTestArticle(1L);
        NewsArticle article2 = createTestArticle(2L);
        List<NewsArticle> articles = List.of(article1, article2);
        String webhookUrl = "https://discord.webhook.url";

        NewsTranslationService.ProcessedNews processedNews = NewsTranslationService.ProcessedNews.builder()
                .title("Translated Title")
                .description("Translated Description")
                .build();

        when(newsTranslationService.processSingleNews(article1.getTitle(), article1.getDescription()))
                .thenReturn(Mono.error(new RuntimeException("Translation error")));
        when(newsTranslationService.processSingleNews(article2.getTitle(), article2.getDescription()))
                .thenReturn(Mono.just(processedNews));
        when(discordService.sendEmbeds(anyString(), any(DiscordWebhookPayload.class)))
                .thenReturn(Mono.empty());

        Mono<List<NewsArticle>> result = batchProcessor.processAndSendToDiscord(articles, webhookUrl);

        StepVerifier.create(result)
                .expectNextMatches(sentArticles -> 
                    sentArticles.size() == 1 && 
                    sentArticles.contains(article2))
                .verifyComplete();

        verify(newsTranslationService, times(2)).processSingleNews(anyString(), anyString());
        verify(discordService, times(1)).sendEmbeds(anyString(), any(DiscordWebhookPayload.class));
    }

    @Test
    void processAndSendToDiscord_shouldHandleDiscordErrors() {
        NewsArticle article1 = createTestArticle(1L);
        NewsArticle article2 = createTestArticle(2L);
        List<NewsArticle> articles = List.of(article1, article2);
        String webhookUrl = "https://discord.webhook.url";

        NewsTranslationService.ProcessedNews processedNews = NewsTranslationService.ProcessedNews.builder()
                .title("Translated Title")
                .description("Translated Description")
                .build();

        when(newsTranslationService.processSingleNews(anyString(), anyString()))
                .thenReturn(Mono.just(processedNews));
        when(discordService.sendEmbeds(anyString(), any(DiscordWebhookPayload.class)))
                .thenReturn(Mono.error(new RuntimeException("Discord error")));

        Mono<List<NewsArticle>> result = batchProcessor.processAndSendToDiscord(articles, webhookUrl);

        StepVerifier.create(result)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

        verify(newsTranslationService, times(2)).processSingleNews(anyString(), anyString());
        verify(discordService, times(1)).sendEmbeds(anyString(), any(DiscordWebhookPayload.class));
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
