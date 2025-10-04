package com.newsbot.service.discord;

import com.newsbot.dto.DiscordWebhookPayload;
import com.newsbot.dto.Embed;
import com.newsbot.model.NewsArticle;
import com.newsbot.service.translation.NewsTranslationService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class NewsDiscordBatchProcessor {

    private final DiscordWebhookService discordService;
    private final NewsTranslationService newsTranslationService;

    @Value("${app.discord.embed-color:3447003}")
    private Integer embedColor;

    @Value("${app.discord.max-embeds-per-message:10}")
    private int maxEmbedsPerMessage;

    private static final int AI_CONCURRENCY = 4;

    @Data
    @AllArgsConstructor
    private static class ArticleEmbed {
        private NewsArticle article;
        private Embed embed;
    }

    public Mono<List<NewsArticle>> processAndSendToDiscord(List<NewsArticle> articles, String webhookUrl) {
        log.info("Iniciando processamento de {} artigos para o Discord", articles.size());

        return Flux.fromIterable(articles)
                .flatMap(article -> {
                            return newsTranslationService.processSingleNews(article.getTitle(), article.getDescription())
                                    .map(processed -> {
                                        return new ArticleEmbed(article, createEmbed(article, processed));
                                    })
                                    .onErrorResume(error -> {
                                        log.warn("Falha ao processar '{}', ignorando artigo: {}",
                                                article.getTitle(), error.getMessage());
                                        return Mono.empty();
                                    });
                        },
                        AI_CONCURRENCY
                )
                .timeout(Duration.ofMinutes(5))
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout global (5 minutos) ao processar artigos para o Discord");
                    } else {
                        log.error("Erro ao processar artigos para o Discord: {}", e.getMessage());
                    }
                    return Flux.empty();
                })
                .collectList()
                .flatMap(articleEmbeds -> {
                    log.info("Coletados {} artigos processados, preparando para envio", articleEmbeds.size());
                    List<Embed> embeds = articleEmbeds.stream()
                            .map(ArticleEmbed::getEmbed)
                            .collect(Collectors.toList());

                    return sendEmbedsInBatches(embeds, webhookUrl, articleEmbeds);
                })
                .timeout(Duration.ofMinutes(10))
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout global (10 minutos) ao enviar artigos para o Discord");
                    } else {
                        log.error("Erro ao enviar artigos para o Discord: {}", e.getMessage());
                    }
                    return Mono.just(new ArrayList<>());
                })
                .doOnSuccess(successfulArticles -> {
                    log.info("Envio para Discord concluido. {} artigos enviados com sucesso", successfulArticles.size());
                });
    }

    private Embed createEmbed(NewsArticle article, NewsTranslationService.ProcessedNews processed) {
        return Embed.builder()
                .title(processed.getTitle())
                .description(processed.getDescription())
                .url(article.getUrl())
                .color(embedColor)
                .timestamp(article.getPublishedDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z")
                .build();
    }

    private Mono<List<NewsArticle>> sendEmbedsInBatches(List<Embed> embeds, String webhookUrl,
                                                 List<ArticleEmbed> articleEmbeds) {
        if (embeds.isEmpty()) {
            log.info("Nenhum embed para enviar");
            return Mono.just(new ArrayList<>());
        }

        Map<Embed, NewsArticle> embedToArticleMap = articleEmbeds.stream()
                .collect(Collectors.toMap(ArticleEmbed::getEmbed, ArticleEmbed::getArticle));

        List<List<Embed>> batches = createBatches(embeds, maxEmbedsPerMessage);
        log.info("Enviando {} embeds em {} lotes", embeds.size(), batches.size());

        return Flux.fromIterable(batches)
                .index()
                .concatMap(tuple -> {
                    long batchIndex = tuple.getT1();
                    List<Embed> batch = tuple.getT2();
                    log.debug("Processando lote {}/{}", batchIndex + 1, batches.size());
                    return sendSingleBatch(batch, webhookUrl);
                })
                .timeout(Duration.ofMinutes(5))
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout global (5 minutos) ao enviar todos os lotes para o Discord");
                    } else {
                        log.error("Erro ao enviar lotes para o Discord: {}", e.getMessage());
                    }
                    return Flux.empty();
                })
                .filter(successfulBatch -> !successfulBatch.isEmpty())
                .flatMap(Flux::fromIterable)
                .map(embedToArticleMap::get)
                .collectList();
    }

    private Mono<List<Embed>> sendSingleBatch(List<Embed> batch, String webhookUrl) {
        DiscordWebhookPayload payload = DiscordWebhookPayload.builder()
                .embeds(batch)
                .build();

        return discordService.sendEmbeds(webhookUrl, payload)
                .thenReturn(batch)
                .doOnSuccess(v -> log.info("Lote de {} embeds enviado com sucesso", batch.size()))
                .delayElement(Duration.ofSeconds(3))
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout (60s) ao enviar lote para o Discord");
                    } else {
                        log.error("Erro ao enviar lote para o Discord: {}", error.getMessage());
                    }
                    return Mono.just(new ArrayList<>());
                });
    }

    private List<List<Embed>> createBatches(List<Embed> embeds, int batchSize) {
        List<List<Embed>> batches = new ArrayList<>();
        for (int i = 0; i < embeds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, embeds.size());
            batches.add(new ArrayList<>(embeds.subList(i, end)));
        }
        return batches;
    }
}
