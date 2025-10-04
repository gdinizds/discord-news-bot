package com.newsbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.news")
public class NewsConfig {
    private List<RssFeed> rssFeeds = new ArrayList<>();

    @Data
    public static class RssFeed {
        private String url;
        private String name;
    }
}
