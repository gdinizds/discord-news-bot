package com.newsbot.service.news;

import com.newsbot.config.NewsConfig;
import com.newsbot.model.NewsArticle;
import com.newsbot.service.duplicate.ContentDuplicateDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RssNewsServiceTest {

    @Mock
    private NewsConfig newsConfig;

    @Mock
    private ContentDuplicateDetector duplicateDetector;

    @InjectMocks
    private RssNewsService rssNewsService;

    @Test
    void fetchAllNews_shouldHandleEmptyFeeds() {
        when(newsConfig.getRssFeeds()).thenReturn(List.of());

        Flux<NewsArticle> result = rssNewsService.fetchAllNews();

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();
    }
}