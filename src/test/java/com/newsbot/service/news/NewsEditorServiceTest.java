package com.newsbot.service.news;

import com.newsbot.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsEditorServiceTest {

    @Mock
    private ChatClient chatClient;

    private NewsEditorService newsEditorService;

    @BeforeEach
    void setUp() {
        newsEditorService = new NewsEditorService(chatClient);
        ReflectionTestUtils.setField(newsEditorService, "topNewsCount", 3);
    }

    @Test
    void selectTopNews_shouldReturnAllArticlesWhenLessThanLimit() {
        List<NewsArticle> articles = List.of(
                createTestArticle(1L, "Test Article 1"),
                createTestArticle(2L, "Test Article 2")
        );

        Mono<List<NewsArticle>> result = newsEditorService.selectTopNews(articles);

        StepVerifier.create(result)
                .assertNext(selectedArticles -> {
                    assertEquals(2, selectedArticles.size());
                    assertEquals("Test Article 1", selectedArticles.get(0).getTitle());
                    assertEquals("Test Article 2", selectedArticles.get(1).getTitle());
                })
                .verifyComplete();

        verifyNoInteractions(chatClient);
    }

    @Test
    void selectTopNews_shouldHandleEmptyArticleList() {
        List<NewsArticle> articles = List.of();

        Mono<List<NewsArticle>> result = newsEditorService.selectTopNews(articles);

        StepVerifier.create(result)
                .assertNext(selectedArticles -> {
                    assertTrue(selectedArticles.isEmpty());
                })
                .verifyComplete();

        verifyNoInteractions(chatClient);
    }


    private NewsArticle createTestArticle(Long id, String title) {
        return NewsArticle.builder()
                .id(id)
                .title(title)
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