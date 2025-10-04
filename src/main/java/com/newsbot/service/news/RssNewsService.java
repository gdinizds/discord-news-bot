package com.newsbot.service.news;

import com.newsbot.config.NewsConfig;
import com.newsbot.model.NewsArticle;
import com.newsbot.service.duplicate.ContentDuplicateDetector;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssNewsService {

    private final NewsConfig newsConfig;
    private final ContentDuplicateDetector duplicateDetector;

    public Flux<NewsArticle> fetchAllNews() {
        return Flux.fromIterable(newsConfig.getRssFeeds())
                .flatMap(this::fetchNewsFromFeed)
                .onErrorContinue((throwable, o) -> {
                    log.error("Erro ao processar feed RSS: {}", throwable.getMessage());
                })
                .doOnNext(article -> log.debug("Artigo processado: {}", article.getTitle()))
                .collectList()
                .flatMapMany(Flux::fromIterable);
    }

    private Flux<NewsArticle> fetchNewsFromFeed(NewsConfig.RssFeed feedConfig) {
        return Mono.fromCallable(() -> {
                    try {
                        log.info("Buscando TODOS os artigos do feed: {}", feedConfig.getName());

                        HttpURLConnection connection = (HttpURLConnection) new URL(feedConfig.getUrl()).openConnection();
                        connection.setRequestProperty("User-Agent",
                                "Mozilla/5.0 (compatible; DiscordNewsBot/1.0; +http://localhost:8080)");
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(15000);

                        String content = fixSelfClosingXmlTags(connection);

                        SyndFeedInput input = new SyndFeedInput();
                        input.setPreserveWireFeed(false);

                        SyndFeed feed = input.build(new java.io.StringReader(content));

                        log.info("Feed '{}': {} artigos encontrados no RSS",
                                feedConfig.getName(), feed.getEntries().size());

                        return feed.getEntries();

                    } catch (FeedException | IOException e) {
                        log.error("Erro ao processar feed RSS de {}: {}",
                                feedConfig.getName(), e.getMessage());
                        throw new RuntimeException("Erro ao buscar RSS de " + feedConfig.getName(), e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(entry -> convertToNewsArticle(entry, feedConfig.getName()))
                .onErrorResume(error -> {
                    log.error("Erro no processamento do feed {}: {}",
                            feedConfig.getName(), error.getMessage());
                    return Flux.empty();
                });
    }

    private String fixSelfClosingXmlTags(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            String content = reader.lines().collect(Collectors.joining("\n"));

            return content
                    .replaceAll("<hr[^>]*(?<!/)>", "<hr/>")
                    .replaceAll("<br[^>]*(?<!/)>", "<br/>")
                    .replaceAll("<img([^>]*?)(?<!/)>", "<img$1/>")
                    .replaceAll("<input([^>]*?)(?<!/)>", "<input$1/>")
                    .replaceAll("<area([^>]*?)(?<!/)>", "<area$1/>")
                    .replaceAll("<meta([^>]*?)(?<!/)>", "<meta$1/>");
        }
    }

    private NewsArticle convertToNewsArticle(SyndEntry entry, String source) {
        String title = cleanText(entry.getTitle());
        String description = "";

        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            description = cleanText(entry.getDescription().getValue());
        } else if (!entry.getContents().isEmpty() && entry.getContents().getFirst().getValue() != null) {
            description = cleanText(entry.getContents().getFirst().getValue());
        }

        String url = entry.getLink();

        String content = title + " " + description;
        String contentHash = duplicateDetector.generateContentHash(content);

        Date publishedDate = entry.getPublishedDate();
        LocalDateTime localPublishedDate = publishedDate != null ?
                publishedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() :
                LocalDateTime.now();

        return NewsArticle.builder()
                .title(title)
                .description(description)
                .url(url)
                .contentHash(contentHash)
                .source(source)
                .publishedDate(localPublishedDate)
                .createdAt(LocalDateTime.now())
                .sentToDiscord(false)
                .build();
    }

    private String cleanText(String text) {
        if (text == null) return "";

        return text
                .replaceAll("<[^>]+>", "")
                .replaceAll("&quot;", "\"")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
