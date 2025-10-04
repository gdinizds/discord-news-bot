package com.newsbot.controller;

import com.newsbot.scheduler.DailyNewsScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {
    private final DailyNewsScheduler dailyNewsScheduler;


    @PostMapping("/execute")
    public Mono<Map<String, Object>> executeManually() {
        return dailyNewsScheduler.executeManually()
                .map(count -> {
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "Job executed successfully",
                            "articlesProcessed", count
                    );
                    return response;
                })
                .onErrorReturn(createErrorResponse("Error executing the job"));
    }

    private Map<String, Object> createErrorResponse(String message) {
        return Map.of(
                "success", false,
                "message", message
        );
    }
}
