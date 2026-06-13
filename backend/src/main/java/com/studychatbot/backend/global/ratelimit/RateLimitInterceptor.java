package com.studychatbot.backend.global.ratelimit;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * LLM 호출 엔드포인트에 사용자별 레이트리밋을 적용하는 인터셉터.
 * 대상 경로는 WebMvcConfig에서 지정한다(컨트롤러/서비스 로직은 건드리지 않는다).
 *
 * preHandle은 컨트롤러 진입 전에 실행되므로, SSE 스트리밍 엔드포인트도
 * SseEmitter 생성(스트림 시작) 전에 차단된다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE는 비동기 처리 후 ASYNC 디스패치로 preHandle이 다시 호출될 수 있다.
        // 최초 REQUEST 디스패치에서만 카운트해 한 요청이 두 번 집계되는 것을 막는다.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 대상 경로는 모두 인증 필수라 정상 흐름에선 인증이 존재한다.
        // 방어적으로, 인증 정보가 없으면 레이트리밋을 건너뛰고 Security가 처리하도록 둔다.
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }

        // 한도 초과 시 던지는 RateLimitExceededException은 컨트롤러 진입 전에 발생하며,
        // DispatcherServlet 예외 리졸버를 거쳐 GlobalExceptionHandler가 429 JSON으로 변환한다.
        // 식별자는 JWT에서 추출된 이메일(authentication.getName())을 사용한다.
        rateLimitService.checkAndConsume(authentication.getName());
        return true;
    }
}
