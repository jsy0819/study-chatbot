package com.studychatbot.backend.domain.quiz.dto;

import com.studychatbot.backend.domain.quiz.entity.QuizSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 퀴즈 생성 API 응답.
 * sessionId와 정답 없는 문제 목록만 반환한다.
 */
@Getter
@RequiredArgsConstructor
public class QuizSessionCreateResponse {

    private final Long sessionId;
    private final List<QuizQuestionResponse> questions;

    public static QuizSessionCreateResponse from(QuizSession session) {
        List<QuizQuestionResponse> questions = session.getQuestions().stream()
                .map(QuizQuestionResponse::from)
                .toList();
        return new QuizSessionCreateResponse(session.getId(), questions);
    }
}
