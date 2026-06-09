package com.studychatbot.backend.domain.quiz.dto;

import com.studychatbot.backend.domain.quiz.entity.QuizQuestion;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 생성 응답 전용 문제 DTO.
 * answerIndex와 explanation을 의도적으로 제외 — 퀴즈 풀기 전 정답 노출 방지.
 */
@Getter
@RequiredArgsConstructor
public class QuizQuestionResponse {

    private final Long id;
    private final int questionOrder;
    private final String question;
    private final List<String> choices;

    public static QuizQuestionResponse from(QuizQuestion q) {
        return new QuizQuestionResponse(q.getId(), q.getQuestionOrder(), q.getQuestion(), q.getChoices());
    }
}
