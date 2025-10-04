package com.newsbot.service.translation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
public class NewsAIResponseParser {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^T[ÍI]TULO\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("(?i)RESUMO\\s*[:：]\\s*(.*)", Pattern.DOTALL);

    public NewsTranslationService.ProcessedNews parseResponse(String response, String originalTitle, String originalDescription,
                                       int maxTitleLength, int maxDescriptionLength) {
        log.debug("Iniciando análise da resposta da IA");

        String title = extractSection(response, TITLE_PATTERN, originalTitle);
        String description = extractSection(response, SUMMARY_PATTERN, originalDescription);

        return NewsTranslationService.ProcessedNews.builder()
                .title(truncate(title, maxTitleLength))
                .description(truncate(description, maxDescriptionLength))
                .build();
    }

    private String extractSection(String text, Pattern pattern, String defaultValue) {
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim().replaceAll("^[\"']|[\"']$", "");
            log.debug("Seção extraída com sucesso usando padrão");
            return extracted;
        }
        log.debug("Padrão não encontrado, usando valor padrão");
        return defaultValue != null ? defaultValue : "";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;

        String truncated = text.substring(0, maxLength - 3) + "...";
        log.debug("Text truncated from {} to {} characters", text.length(), maxLength);
        return truncated;
    }
}
