package com.studychatbot.backend.domain.quiz.dto;

import com.studychatbot.backend.domain.quiz.entity.QuizSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class QuizSessionSummary {

    private final Long sessionId;
    private final String documentTitle;
    private final int questionCount;
    private final Integer score;
    private final LocalDateTime createdAt;
    private final LocalDateTime submittedAt;

    public static QuizSessionSummary from(QuizSession session) {
        return new QuizSessionSummary(
                session.getId(),
                session.getDocument().getTitle(),
                session.getQuestionCount(),
                session.getScore(),
                session.getCreatedAt(),
                session.getSubmittedAt()
        );
    }
}
