package com.studychatbot.backend.global.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studychatbot.backend.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청(토큰 없음·만료·무효)에 401을 반환한다.
 * httpBasic·formLogin을 끄면 Spring Security 기본 EntryPoint가
 * Http403ForbiddenEntryPoint(403)로 떨어지는데, 이는 의미상 부정확하다.
 * 401(미인증)과 403(권한 없음, ForbiddenException)을 명확히 구분해
 * 프론트가 만료 시에만 토큰 재발급을 시도하도록 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse body = ErrorResponse.builder()
            .code("UNAUTHORIZED")
            .message("인증이 필요합니다.")
            .build();

        objectMapper.writeValue(response.getWriter(), body);
    }
}
