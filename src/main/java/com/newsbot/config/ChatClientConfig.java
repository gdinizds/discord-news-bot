package com.newsbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("Você é um assistente especializado em resumir notícias de tecnologia e jogos. " +
                        "Sua tarefa é criar resumos concisos, informativos e envolventes em português brasileiro. " +
                        "Foque nos pontos mais importantes e interessantes, use linguagem clara e acessível, " +
                        "preserve informações técnicas relevantes e evite repetição de informações do título.")
                .build();
    }
}
