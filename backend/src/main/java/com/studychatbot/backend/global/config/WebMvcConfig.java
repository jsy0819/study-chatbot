package com.studychatbot.backend.global.config;

import com.studychatbot.backend.global.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 인터셉터 등록.
 * 레이트리밋은 LLM(Gemini)을 호출하는 엔드포인트에만 적용한다.
 * 자료 업로드 등 LLM 비용과 무관한 엔드포인트는 대상에서 제외한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/chat", "/api/chat/stream", "/api/quiz/generate");
    }
}
