package com.newsbot.scheduler;

import com.newsbot.model.NewsArticle;
import com.newsbot.service.news.NewsEditorService;
import com.newsbot.service.news.RssNewsService;
import com.newsbot.service.persistence.NewsArticlePersistenceService;
import com.newsbot.service.discord.NewsDiscordBatchProcessor;
import com.newsbot.service.filter.NewsFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyNewsScheduler {

    private final RssNewsService rssNewsService;
    private final NewsFilterService newsFilterService;
    private final NewsArticlePersistenceService newsArticlePersistenceService;
    private final NewsEditorService newsEditorService;
    private final NewsDiscordBatchProcessor discordBatchProcessor;

    @Value("${app.discord.webhook-url}")
    private String webhookUrl;

    @Scheduled(cron = "0 0 11 * * *", zone = "America/Sao_Paulo")
    public void executeDaily() {
        log.info("Iniciando job diario de noticias");

        processAndSendNews()
                .subscribe(
                        count -> log.info("Job concluido com sucesso. {} noticias processadas", count),
                        error -> log.error("Erro no job diario: {}", error.getMessage(), error)
                );
    }

    public Mono<Integer> executeManually() {
        log.info("Executando job manualmente");
        return processAndSendNews()
                .timeout(Duration.ofMinutes(10))
                .doOnError(error -> log.error("Timeout ou erro na execucao manual: {}", error.getMessage()));
    }


    private Mono<Integer> processAndSendNews() {
        return rssNewsService.fetchAllNews()
                .onErrorContinue((throwable, o) -> {
                    log.error("Erro ao processar artigo individual: {}", throwable.getMessage());
                })
                .flatMap(newsFilterService::filterDuplicates)
                .flatMap(newsArticlePersistenceService::saveArticle)
                .collectList()
                .flatMap(this::processArticles)
                .onErrorResume(error -> {
                    log.error("Erro no processamento geral: {}", error.getMessage(), error);
                    return Mono.just(-1);
                })
                .defaultIfEmpty(0);
    }

    private Mono<Integer> processArticles(List<NewsArticle> articles) {
        if (articles.isEmpty()) {
            log.info("Nenhuma noticia nova encontrada");
            return Mono.just(0);
        }

        log.info("Encontradas {} noticias novas para processar", articles.size());

        return newsEditorService.selectTopNews(articles)
                .flatMap(selectedArticles -> {
                    if (selectedArticles.isEmpty()) {
                        log.info("IA nao selecionou nenhuma noticia");
                        return Mono.just(0);
                    }

                    log.info("IA selecionou {} de {} noticias para enviar",
                            selectedArticles.size(), articles.size());

                    return processSelectedArticles(selectedArticles)
                            .doOnError(e -> log.error("Erro no processamento de artigos selecionados: {}", e.getMessage()))
                            .then(Mono.just(selectedArticles.size()));
                });
    }

    private Mono<Void> processSelectedArticles(List<NewsArticle> selectedArticles) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.warn("DISCORD_WEBHOOK_URL nao configurada. Pulando envio para Discord");
            return Mono.empty();
        }

        log.info("Processando {} artigos selecionados com IA para traducao/resumo", selectedArticles.size());

        return discordBatchProcessor.processAndSendToDiscord(selectedArticles, webhookUrl)
                .timeout(Duration.ofMinutes(15))
                .doOnError(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout global (15 minutos) ao processar e enviar artigos para o Discord");
                    } else {
                        log.error("Erro ao processar e enviar artigos para o Discord: {}", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Erro no processamento do Discord, continuando com 0 artigos: {}", e.getMessage());
                    return Mono.just(new ArrayList<>());
                })
                .flatMap(successfulArticles -> {
                    if (successfulArticles.isEmpty()) {
                        log.warn("Nenhum artigo foi enviado com sucesso para o Discord");
                        return Mono.empty();
                    }

                    log.info("{} de {} artigos foram enviados com sucesso para o Discord", 
                            successfulArticles.size(), selectedArticles.size());

                    return newsArticlePersistenceService.markArticlesAsSent(successfulArticles)
                            .timeout(Duration.ofMinutes(2))
                            .onErrorResume(e -> {
                                log.error("Erro ao marcar artigos como enviados: {}", e.getMessage());
                                return Mono.empty();
                            });
                })
                .doOnSuccess(v -> log.info("Processamento e envio concluido com sucesso"));
    }
}
