package com.studychatbot.backend.domain.quiz.controller;

import com.studychatbot.backend.domain.quiz.dto.*;
import com.studychatbot.backend.domain.quiz.service.QuizService;
import com.studychatbot.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /** 퀴즈 생성 + DB 저장. 응답에 정답(answerIndex)·해설 미포함. */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<QuizSessionCreateResponse>> generate(
            @Valid @RequestBody QuizGenerationRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        QuizSessionCreateResponse response = quizService.generate(email, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /** 답안 제출 및 채점. 이 응답부터 정답·해설 공개. */
    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<ApiResponse<QuizSubmitResponse>> submit(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody QuizSubmitRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        QuizSubmitResponse response = quizService.submit(email, sessionId, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /** 내 퀴즈 세션 목록 조회 (요약). */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<QuizSessionSummary>>> getMySessions(
            Authentication authentication) {
        String email = authentication.getName();
        List<QuizSessionSummary> sessions = quizService.getMySessions(email);
        return ResponseEntity.ok(ApiResponse.of(sessions));
    }

    /** 특정 세션 상세 조회. 소유권 검증 후 정답·해설 포함 반환. */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<QuizSessionDetailResponse>> getSessionDetail(
            @PathVariable("sessionId") Long sessionId,
            Authentication authentication) {
        String email = authentication.getName();
        QuizSessionDetailResponse response = quizService.getSessionDetail(email, sessionId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
