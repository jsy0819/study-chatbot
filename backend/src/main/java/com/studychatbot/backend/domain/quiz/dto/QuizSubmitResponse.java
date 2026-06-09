package com.studychatbot.backend.domain.quiz.dto;

import com.studychatbot.backend.domain.quiz.entity.QuizSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class QuizSubmitResponse {

    private final int score;
    private final int totalCount;
    private final List<QuizResultItem> results;

    public static QuizSubmitResponse from(QuizSession session) {
        List<QuizResultItem> results = session.getQuestions().stream()
                .map(QuizResultItem::from)
                .toList();
        return new QuizSubmitResponse(session.getScore(), session.getQuestionCount(), results);
    }
}
