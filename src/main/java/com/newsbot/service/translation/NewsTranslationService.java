package com.newsbot.service.translation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsTranslationService {

    private final ChatClient chatClient;
    private final LanguageDetectionService languageDetectionService;
    private final NewsAIResponseParser responseParser;

    @Value("${app.discord.max-description-length:400}")
    private int maxDescriptionLength;

    @Value("${app.discord.max-title-length:200}")
    private int maxTitleLength;

    public Flux<ProcessedNews> processAllNews(Flux<NewsInput> newsFlux) {
        return newsFlux.flatMap(news ->
                processSingleNews(news.getTitle(), news.getDescription())
                        .onErrorResume(error -> {
                            log.warn("Falha ao processar notícia '{}': {} - ignorando notícia e continuando",
                                    news.getTitle(), error.getMessage());
                            return Mono.empty();
                        })
        );
    }

    public Mono<ProcessedNews> processSingleNews(String originalTitle, String originalDescription) {
        return translateWithAI(originalTitle, originalDescription)
                .timeout(Duration.ofSeconds(90))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(3))
                        .maxBackoff(Duration.ofSeconds(8))
                        .filter(this::isRetryableError))
                .onErrorResume(error -> {
                    log.warn("Falha persistente na tradução da notícia '{}': {} - ignorando",
                            originalTitle, error.getMessage());
                    return Mono.empty();
                })
                .flatMap(result -> {
                    if (!languageDetectionService.isPortuguese(result.getTitle()) ||
                            !languageDetectionService.isPortuguese(result.getDescription())) {
                        log.warn("Tradução inconsistente para '{}', descartando notícia", result.getTitle());
                        return Mono.empty();
                    }
                    log.info("Tradução concluída: '{}'", result.getTitle());
                    return Mono.just(result);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ProcessedNews> translateWithAI(String title, String description) {
        String prompt = buildTranslationPrompt(title, description);

        return Mono.fromCallable(() -> {
                    String response = chatClient.prompt()
                            .user(prompt)
                            .call()
                            .content();
                    log.debug("Resposta da IA recebida para tradução da notícia: '{}'", title);
                    return responseParser.parseResponse(response, title, description, maxTitleLength, maxDescriptionLength);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("Erro ao traduzir notícia '{}': {} - ignorando notícia",
                            title, ex.getMessage());
                    return Mono.empty();
                });
    }

    private String buildTranslationPrompt(String title, String description) {
        return String.format("""
            Você é um tradutor profissional especializado em notícias de tecnologia e jogos.
            Traduza o conteúdo abaixo para PORTUGUÊS BRASILEIRO, mantendo clareza e substantivos próprios.

            Regras:
            - Sempre responda 100%% em português brasileiro
            - Não traduza substantivos próprios (empresas, produtos)
            - Resuma de maneira fluida e técnica
            - Título máximo de %d caracteres, Resumo máximo de %d caracteres
            - Se já estiver em português, apenas melhore a escrita

            TÍTULO ORIGINAL: %s
            DESCRIÇÃO ORIGINAL: %s

            Responda exatamente neste formato:
            TÍTULO: [tradução]
            RESUMO: [resumo em português]
            """,
                maxTitleLength,
                maxDescriptionLength,
                title,
                description != null ? description : title
        );
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof InterruptedException) return true;
        if (error instanceof java.net.SocketTimeoutException) return true;
        if (error instanceof java.io.IOException) return true;
        if (error instanceof org.springframework.web.client.ResourceAccessException) return true;

        String msg = error.getMessage();
        return msg != null && (
                msg.contains("timeout") ||
                msg.contains("I/O error") ||
                msg.contains("Connection reset") ||
                msg.contains("ReadTimeout") ||
                msg.contains("ConnectException")
        );
    }

    @lombok.Data
    @lombok.Builder
    public static class ProcessedNews {
        private String title;
        private String description;
    }

    @lombok.Data
    @lombok.Builder
    public static class NewsInput {
        private String title;
        private String description;
    }
}
