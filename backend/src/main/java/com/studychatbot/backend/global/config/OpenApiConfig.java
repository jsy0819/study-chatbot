package com.studychatbot.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        // JWT Bearer 인증 스킴 정의 — Swagger UI "Authorize" 버튼에 표시됨
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        return new OpenAPI()
                .info(new Info()
                        .title("스터디 챗봇 API")
                        .description("학습자료 기반 스터디 챗봇 — 회원 인증, 자료 관리, Q&A API")
                        .version("v1"))
                // 전역 보안 요구사항: 모든 API에 bearerAuth 적용 (Authorize 버튼으로 토큰 입력 시 자동 헤더 삽입)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerAuth));
    }
}
