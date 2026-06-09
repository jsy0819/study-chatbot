package com.studychatbot.backend.domain.quiz.controller;

import com.studychatbot.backend.domain.quiz.dto.QuizGenerationRequest;
import com.studychatbot.backend.domain.quiz.dto.QuizGenerationResponse;
import com.studychatbot.backend.domain.quiz.service.QuizService;
import com.studychatbot.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<QuizGenerationResponse>> generate(
            @Valid @RequestBody QuizGenerationRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        QuizGenerationResponse response = quizService.generate(email, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
