package com.newsbot.controller;

import com.newsbot.scheduler.DailyNewsScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    @Mock
    private DailyNewsScheduler dailyNewsScheduler;

    @InjectMocks
    private NewsController newsController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(newsController).build();
    }

    @Test
    void executeManually_shouldReturnSuccessResponse() {
        when(dailyNewsScheduler.executeManually()).thenReturn(Mono.just(5));

        webTestClient.post()
                .uri("/api/news/execute")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(response -> {
                    assert Boolean.TRUE.equals(response.get("success"));
                    assert "Job executed successfully".equals(response.get("message"));
                    assert Integer.valueOf(5).equals(response.get("articlesProcessed"));
                });
    }

    @Test
    void executeManually_shouldHandleError() {
        when(dailyNewsScheduler.executeManually()).thenReturn(Mono.error(new RuntimeException("Test error")));

        webTestClient.post()
                .uri("/api/news/execute")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(response -> {
                    assert Boolean.FALSE.equals(response.get("success"));
                    assert "Error executing the job".equals(response.get("message"));
                });
    }
}
