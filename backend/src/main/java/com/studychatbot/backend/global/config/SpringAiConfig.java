package com.studychatbot.backend.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    // Spring AI 스타터가 auto-configure한 ChatClient.Builder를 받아 Bean으로 등록.
    // ChatService 등 여러 컴포넌트가 ChatClient를 공유할 수 있도록 단일 Bean으로 분리.
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
