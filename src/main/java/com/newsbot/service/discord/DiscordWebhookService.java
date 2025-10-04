package com.newsbot.service.discord;

import com.newsbot.dto.DiscordWebhookPayload;
import com.newsbot.dto.Embed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {
    private final WebClient webClient;

    @Value("${app.discord.max-embeds-per-message:10}")
    private int maxEmbedsPerMessage;

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_DESCRIPTION_LENGTH = 4096;
    private static final int MAX_TOTAL_CHARACTERS = 6000;


    public Mono<Void> sendEmbeds(String webhookUrl, DiscordWebhookPayload payload) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("URL do Webhook não configurada"));
        }

        DiscordWebhookPayload sanitizedPayload = sanitizePayload(payload);
        log.debug("Enviando payload para Discord: {} embeds", 
                sanitizedPayload.getEmbeds() != null ? sanitizedPayload.getEmbeds().size() : 0);

        return webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sanitizedPayload)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(30))
                .doOnSubscribe(s -> log.debug("Iniciando envio para Discord"))
                .doOnSuccess(response -> log.info("Mensagem enviada para o Discord com sucesso"))
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.error("Timeout (30s) ao enviar mensagem para o Discord");
                    } else if (error instanceof WebClientResponseException webEx) {
                        if (webEx.getStatusCode().is4xxClientError()) {
                            log.error("Erro 4xx do Discord - Corpo do formulário inválido: {}",
                                    webEx.getResponseBodyAsString());
                        } else {
                            log.error("Erro do Discord: {} - {}",
                                    webEx.getStatusCode(), webEx.getResponseBodyAsString());
                        }
                    } else {
                        log.error("Erro ao enviar para o Discord: {}", error.getMessage());
                    }
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .doBeforeRetry(rs -> log.warn("Tentativa {} de envio para Discord após erro: {}", 
                                rs.totalRetries() + 1, rs.failure().getMessage()))
                        .filter(this::isRetryableError))
                .then();
    }


    private DiscordWebhookPayload sanitizePayload(DiscordWebhookPayload payload) {
        List<Embed> sanitizedEmbeds = new ArrayList<>();
        int totalCharacters = 0;

        if (payload.getEmbeds() != null) {
            for (Embed embed : payload.getEmbeds()) {
                if (sanitizedEmbeds.size() >= maxEmbedsPerMessage) {
                    break;
                }

                Embed sanitizedEmbed = sanitizeEmbed(embed);
                int embedCharacters = calculateEmbedCharacters(sanitizedEmbed);

                if (totalCharacters + embedCharacters > MAX_TOTAL_CHARACTERS) {
                    log.warn("Limite de caracteres atingido, parando no embed: {}",
                            sanitizedEmbed.getTitle());
                    break;
                }

                if (isValidEmbed(sanitizedEmbed)) {
                    sanitizedEmbeds.add(sanitizedEmbed);
                    totalCharacters += embedCharacters;
                }
            }
        }

        String content = payload.getContent();
        if ((content == null || content.trim().isEmpty()) && sanitizedEmbeds.isEmpty()) {
            content = " ";
        }
        return DiscordWebhookPayload.builder()
                .content(content)
                .embeds(sanitizedEmbeds.isEmpty() ? null : sanitizedEmbeds)
                .build();
    }


    private Embed sanitizeEmbed(Embed embed) {
        String title = truncateString(embed.getTitle(), MAX_TITLE_LENGTH);
        String description = truncateString(embed.getDescription(), MAX_DESCRIPTION_LENGTH);
        String url = sanitizeUrl(embed.getUrl());
        return Embed.builder()
                .title(title)
                .description(description)
                .url(url)
                .color(embed.getColor())
                .timestamp(embed.getTimestamp())
                .build();
    }

    private String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    private String sanitizeUrl(String url) {
        if (url == null || !URL_PATTERN.matcher(url).matches()) {
            return null;
        }
        return url;
    }

    private boolean isValidEmbed(Embed embed) {
        return embed.getTitle() != null && !embed.getTitle().trim().isEmpty() ||
                embed.getDescription() != null && !embed.getDescription().trim().isEmpty();
    }

    private int calculateEmbedCharacters(Embed embed) {
        int count = 0;
        if (embed.getTitle() != null) count += embed.getTitle().length();
        if (embed.getDescription() != null) count += embed.getDescription().length();
        return count;
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof WebClientResponseException webEx) {
            return webEx.getStatusCode().is5xxServerError() || webEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }
        return false;
    }
}
