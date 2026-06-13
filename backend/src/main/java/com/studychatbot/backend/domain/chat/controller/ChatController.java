package com.studychatbot.backend.domain.chat.controller;

import com.studychatbot.backend.domain.chat.dto.ChatRequest;
import com.studychatbot.backend.domain.chat.dto.ChatResponse;
import com.studychatbot.backend.domain.chat.dto.ChatSessionDetailResponse;
import com.studychatbot.backend.domain.chat.dto.ChatSessionSummary;
import com.studychatbot.backend.domain.chat.service.ChatHistoryService;
import com.studychatbot.backend.domain.chat.service.ChatService;
import com.studychatbot.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        ChatResponse response = chatService.chat(email, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * SSE 스트리밍 엔드포인트. fetch() + Authorization 헤더로 호출한다 (EventSource는 헤더 미지원).
     * 검증·RAG 검색 중 예외가 발생하면 SseEmitter 생성 전에 던져지므로
     * GlobalExceptionHandler가 일반 HTTP 오류(403/404 등)로 응답한다.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        // 동기 처리(세션 확보 + USER 저장 + 검증 + RAG 검색)는 여기서 완료. 이후 Flux는 LLM 스트리밍만 담당.
        ChatService.ChatStream stream = chatService.streamAnswer(email, request);

        SseEmitter emitter = new SseEmitter(180_000L); // LLM 응답 최대 3분 허용

        // 토큰 전송 전에 sessionId를 먼저 내려준다 — 프론트가 이어쓰기(같은 세션 후속 질문)에 사용.
        try {
            emitter.send(SseEmitter.event().name("session").data(stream.sessionId()));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        Disposable subscription = stream.tokens().subscribe(
            token -> {
                try {
                    emitter.send(SseEmitter.event().data(token));
                } catch (IOException e) {
                    // 클라이언트가 연결을 끊으면 send()가 IOException — 구독도 함께 중단
                    emitter.completeWithError(e);
                }
            },
            emitter::completeWithError,
            emitter::complete
        );

        // SSE 연결이 어떤 이유로든 끝나면 Flux 구독 해제 → 진행 중인 LLM 호출 취소
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            subscription.dispose();
            emitter.complete();
        });

        return emitter;
    }

    /** 내 채팅 세션 목록 조회 (요약). */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ChatSessionSummary>>> getMySessions(
            Authentication authentication) {
        String email = authentication.getName();
        List<ChatSessionSummary> sessions = chatHistoryService.getMySessions(email);
        return ResponseEntity.ok(ApiResponse.of(sessions));
    }

    /** 특정 세션 상세 조회 (메시지 목록). 소유권 검증 후 반환. */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<ChatSessionDetailResponse>> getSessionDetail(
            @PathVariable("sessionId") Long sessionId,
            Authentication authentication) {
        String email = authentication.getName();
        ChatSessionDetailResponse response = chatHistoryService.getSessionDetail(email, sessionId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /** 세션 삭제 (cascade로 메시지도 함께 삭제). 소유권 검증 후 수행. */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable("sessionId") Long sessionId,
            Authentication authentication) {
        String email = authentication.getName();
        chatHistoryService.deleteSession(email, sessionId);
        return ResponseEntity.ok(ApiResponse.<Void>of(null));
    }
}
