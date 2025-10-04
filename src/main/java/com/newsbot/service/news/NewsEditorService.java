package com.newsbot.service.news;

import com.newsbot.model.NewsArticle;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsEditorService {

    private final ChatClient chatClient;

    @Value("${app.news.top-news-count:20}")
    private int topNewsCount;

    private static final String EDITOR_SYSTEM_PROMPT = """
        Você é um editor-chefe experiente de um portal brasileiro de tecnologia e jogos.
        Avalie APENAS com base no título da notícia e dê uma pontuação de 1 a 10.

        Critérios:
        10 - Lançamentos revolucionários, grandes aquisições
        8-9 - Atualizações importantes, jogos AAA muito aguardados
        6-7 - Notícias interessantes de empresas conhecidas
        4-5 - Conteúdo de nicho ou atualizações menores
        1-3 - Baixa relevância ou muito específico

        SEMPRE RESPONDA no formato: NOTA1: X, NOTA2: Y, NOTA3: Z
        """;


    public Mono<List<NewsArticle>> selectTopNews(List<NewsArticle> allArticles) {
        if (allArticles.size() <= topNewsCount) {
            log.info("Apenas {} artigos disponíveis, enviando todos", allArticles.size());
            return Mono.just(allArticles);
        }

        log.info("Editor IA: Avaliando {} artigos de notícias para selecionar os {} melhores...",
                allArticles.size(), topNewsCount);

        return evaluateNewsSequentially(allArticles)
                .collectList()
                .map(this::selectBestNews)
                .timeout(Duration.ofMinutes(10))  // Timeout global para todo o processo
                .doOnError(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout global (10 minutos) ao avaliar artigos. Usando seleção alternativa.");
                    } else {
                        log.error("Erro ao avaliar artigos: {}", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Usando seleção alternativa devido a erro: {}", e.getMessage());
                    return Mono.just(createFallbackArticleSelection(allArticles));
                })
                .doOnNext(selected -> {
                    log.info("Editor IA: Selecionados {} artigos de notícias de qualidade", selected.size());
                    log.info("Processamento de avaliação de notícias concluído com sucesso");
                });
    }

    private List<NewsArticle> createFallbackArticleSelection(List<NewsArticle> allArticles) {
        log.info("Criando seleção alternativa de artigos");
        return allArticles.stream()
                .sorted(Comparator.comparing(NewsArticle::getPublishedDate).reversed())
                .limit(topNewsCount)
                .toList();
    }

    private Flux<NewsEvaluation> evaluateNewsSequentially(List<NewsArticle> articles) {
        log.info("Processando todos os {} artigos de uma vez", articles.size());

        return evaluateBatchWithRetry(articles)
                .timeout(Duration.ofSeconds(180)) // 3 minutos de timeout para todos os artigos
                .doOnError(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout (180s) ao processar todos os {} artigos", articles.size());
                    } else {
                        log.error("Erro ao processar todos os {} artigos: {}", articles.size(), e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Usando avaliação alternativa para todos os artigos devido a erro: {}", e.getMessage());
                    return Flux.fromIterable(createFallbackSelection(articles));
                })
                .doOnComplete(() -> {
                    log.info("Todos os {} artigos processados com sucesso", articles.size());
                });
    }

    private Flux<NewsEvaluation> evaluateBatchWithRetry(List<NewsArticle> batch) {
        return evaluateBatchAsync(batch)
                .timeout(Duration.ofSeconds(60))
                .doOnSubscribe(s -> log.info("Iniciando avaliação de lote com {} artigos", batch.size()))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(3))
                        .maxBackoff(Duration.ofSeconds(10))
                        .maxAttempts(3)
                        .doBeforeRetry(rs -> log.warn("Tentativa {} de avaliação de lote após erro: {}", 
                                rs.totalRetries() + 1, rs.failure().getMessage()))
                        .filter(this::isRetryableError))
                .onErrorResume(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout (60s) ao avaliar lote de {} artigos", batch.size());
                    } else {
                        log.error("Avaliação em lote falhou após tentativas: {}", error.toString());
                    }
                    return Flux.fromIterable(createFallbackSelection(batch));
                });
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof InterruptedException) return true;
        if (error instanceof org.springframework.web.client.ResourceAccessException) return true;
        if (error instanceof java.net.SocketTimeoutException) return true;
        if (error instanceof java.io.IOException) return true;
        if (error instanceof java.util.concurrent.TimeoutException) return true;

        String msg = error.getMessage();
        return msg != null && (
                msg.contains("timeout") ||
                msg.contains("Connection reset") ||
                msg.contains("I/O error")
        );
    }

    private Flux<NewsEvaluation> evaluateBatchAsync(List<NewsArticle> batch) {
        return Mono.fromCallable(() -> buildPrompt(batch))
                .flatMap(prompt -> Mono.defer(() -> callChatApi(prompt))
                        .subscribeOn(Schedulers.boundedElastic())
                        .timeout(Duration.ofSeconds(45))  // Timeout específico para a chamada à API
                        .doOnError(e -> {
                            if (e instanceof java.util.concurrent.TimeoutException) {
                                log.error("Timeout (45s) na chamada à API para lote de {} artigos", batch.size());
                            }
                        })
                )
                .map(response -> {
                    List<NewsEvaluation> evaluations = parseEvaluationResponse(response, batch);
                    return evaluations;
                })
                .timeout(Duration.ofSeconds(50))  // Timeout global para todo o processamento do lote
                .onErrorResume(e -> {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt(); // respect cancellation
                    }

                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout global (50s) no processamento do lote de {} artigos", batch.size());
                    } else {
                        log.warn("Erro de avaliação ({}): {}", e.getClass().getSimpleName(), e.getMessage());
                    }

                    List<NewsEvaluation> fallback = createFallbackSelection(batch);
                    return Mono.just(fallback);
                })
                .flatMapMany(Flux::fromIterable);
    }

    private String buildPrompt(List<NewsArticle> batch) {
        StringBuilder prompt = new StringBuilder("Evaluate these news titles (1-10):\n\n");
        for (int i = 0; i < batch.size(); i++) {
            NewsArticle article = batch.get(i);
            prompt.append(String.format("%d. [%s] %s%n", i + 1, article.getSource(), article.getTitle()));
        }
        prompt.append("\nRESPONSE (NOTA1: X, NOTA2: Y, ...):");
        return prompt.toString();
    }

    private Mono<String> callChatApi(String prompt) {
        return Mono.fromCallable(() -> {
            log.debug("Enviando prompt para API de IA ({} caracteres)", prompt.length());
            long startTime = System.currentTimeMillis();

            try {
                String response = chatClient.prompt()
                        .system(EDITOR_SYSTEM_PROMPT)
                        .user(prompt)
                        .call()
                        .content();

                long duration = System.currentTimeMillis() - startTime;
                log.debug("Resposta da API de IA recebida em {}ms ({} caracteres)", 
                        duration, response.length());
                return response;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.warn("Erro ao chamar API de IA após {}ms: {}", duration, e.getMessage());
                throw e;
            }
        })
        .timeout(Duration.ofSeconds(30))
        .doOnError(e -> {
            if (e instanceof java.util.concurrent.TimeoutException) {
                log.error("Timeout ao chamar API de IA (30s)");
            } else {
                log.error("Erro ao chamar API de IA: {}", e.getMessage());
            }
        })
        .onErrorResume(e -> {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Falha na chamada à API de IA, retornando resposta vazia: {}", e.getMessage());
            return Mono.just(""); // Retorna string vazia que será tratada pelo parseEvaluationResponse
        });
    }

    private List<NewsEvaluation> createFallbackSelection(List<NewsArticle> batch) {
        log.warn("Usando seleção alternativa para {} artigos", batch.size());
        List<NewsEvaluation> fallbackEvals = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            NewsArticle article = batch.get(i);
            int score = calculateBasicScore(article, i);
            fallbackEvals.add(new NewsEvaluation(article, score));
        }
        return fallbackEvals;
    }

    private int calculateBasicScore(NewsArticle article, int position) {
        int score = 6;
        String source = article.getSource().toLowerCase();
        if (source.contains("techcrunch") || source.contains("verge") || source.contains("ars technica")) score += 2;
        else if (source.contains("polygon") || source.contains("pc gamer")) score += 1;
        if (position < 3) score += 1;
        else if (position >= 5) score -= 1;
        return Math.min(10, Math.max(3, score));
    }

    private List<NewsEvaluation> parseEvaluationResponse(String response, List<NewsArticle> batch) {
        if (response == null || response.trim().isEmpty()) {
            log.warn("Resposta da API vazia ou nula, usando seleção alternativa");
            return createFallbackSelection(batch);
        }

        log.debug("Analisando resposta da API: {} caracteres", response.length());
        List<NewsEvaluation> evaluations = new ArrayList<>();
        Pattern pattern = Pattern.compile("NOTA(\\d+)\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        int matchCount = 0;

        while (matcher.find()) {
            matchCount++;
            try {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                int score = Integer.parseInt(matcher.group(2));
                if (index >= 0 && index < batch.size() && score >= 1 && score <= 10) {
                    evaluations.add(new NewsEvaluation(batch.get(index), score));
                    log.trace("Avaliação extraída: artigo {} com pontuação {}", index + 1, score);
                } else {
                    log.warn("Avaliação fora dos limites: índice={}, pontuação={}", index + 1, score);
                }
            } catch (NumberFormatException e) {
                log.warn("Erro ao analisar pontuação: {}", matcher.group());
            }
        }

        log.debug("Encontrados {} padrões de avaliação, {} válidos", matchCount, evaluations.size());

        if (evaluations.isEmpty()) {
            log.warn("Nenhuma avaliação válida extraída da resposta: '{}'", 
                    response.length() > 100 ? response.substring(0, 100) + "..." : response);
            return createFallbackSelection(batch);
        }

        if (evaluations.size() < batch.size()) {
            int missingCount = 0;
            for (int i = 0; i < batch.size(); i++) {
                NewsArticle article = batch.get(i);
                boolean found = evaluations.stream().anyMatch(e -> e.article.getUrl().equals(article.getUrl()));
                if (!found) {
                    evaluations.add(new NewsEvaluation(article, calculateBasicScore(article, i)));
                    missingCount++;
                }
            }
            log.debug("Adicionadas {} avaliações alternativas para artigos não avaliados", missingCount);
        }

        return evaluations;
    }

    private List<NewsArticle> selectBestNews(List<NewsEvaluation> evaluations) {
        return evaluations.stream()
                .sorted(Comparator.comparingInt((NewsEvaluation e) -> e.score).reversed())
                .limit(topNewsCount)
                .map(e -> e.article)
                .toList();
    }

    private List<List<NewsArticle>> createBatches(List<NewsArticle> articles, int batchSize) {
        List<List<NewsArticle>> batches = new ArrayList<>();
        for (int i = 0; i < articles.size(); i += batchSize)
            batches.add(articles.subList(i, Math.min(i + batchSize, articles.size())));
        return batches;
    }


    private record NewsEvaluation(NewsArticle article, int score) {}
}
