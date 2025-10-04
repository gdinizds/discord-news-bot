package com.newsbot.service.filter;

import com.newsbot.model.NewsArticle;
import com.newsbot.repository.NewsArticleRepository;
import com.newsbot.service.duplicate.ContentDuplicateDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsFilterService {

    private final NewsArticleRepository newsRepository;
    private final ContentDuplicateDetector contentDuplicateDetector;


    public Mono<NewsArticle> filterDuplicates(NewsArticle article) {
        return newsRepository.findByUrl(article.getUrl())
                .hasElement()
                .flatMap(existsByUrl -> {
                    if (existsByUrl) {
                        log.debug("Article discarded due to duplicate URL: {}", article.getUrl());
                        return Mono.empty();
                    }
                    return checkContentHashDuplicate(article);
                })
                .onErrorReturn(article);
    }


    private Mono<NewsArticle> checkContentHashDuplicate(NewsArticle article) {
        return newsRepository.findByContentHash(article.getContentHash())
                .hasElement()
                .flatMap(existsByHash -> {
                    if (existsByHash) {
                        log.debug("Article discarded due to duplicate content hash: {}", article.getTitle());
                        return Mono.empty();
                    }
                    return checkContentSimilarity(article);
                });
    }

    private Mono<NewsArticle> checkContentSimilarity(NewsArticle article) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        return newsRepository.findRecentArticles(oneDayAgo)
                .any(existing -> {
                    String existingContent = existing.getTitle() + " " + existing.getDescription();
                    String newContent = article.getTitle() + " " + article.getDescription();

                    return contentDuplicateDetector.areContentsSimilar(existingContent, newContent);
                })
                .flatMap(isSimilar -> {
                    if (isSimilar) {
                        log.debug("Article discarded due to content similarity: {}", article.getTitle());
                        return Mono.empty();
                    }
                    return Mono.just(article);
                })
                .onErrorReturn(article);
    }
}
