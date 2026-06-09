package com.studychatbot.backend.domain.quiz.dto;

import com.studychatbot.backend.domain.quiz.entity.QuizSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세션 상세 응답. 정답·해설 포함 — 제출/미제출 모두 반환하되,
 * 미제출 상태면 userAnswerIndex와 correct가 null로 내려간다.
 */
@Getter
@RequiredArgsConstructor
public class QuizSessionDetailResponse {

    private final Long sessionId;
    private final String documentTitle;
    private final int questionCount;
    private final Integer score;
    private final LocalDateTime createdAt;
    private final LocalDateTime submittedAt;
    private final List<QuizResultItem> questions;

    public static QuizSessionDetailResponse from(QuizSession session) {
        List<QuizResultItem> questions = session.getQuestions().stream()
                .map(QuizResultItem::from)
                .toList();
        return new QuizSessionDetailResponse(
                session.getId(),
                session.getDocument().getTitle(),
                session.getQuestionCount(),
                session.getScore(),
                session.getCreatedAt(),
                session.getSubmittedAt(),
                questions
        );
    }
}
