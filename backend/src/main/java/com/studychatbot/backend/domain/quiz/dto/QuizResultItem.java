package com.studychatbot.backend.domain.quiz.dto;

import com.studychatbot.backend.domain.quiz.entity.QuizQuestion;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 제출 응답 및 세션 상세 공통 문제 결과 DTO.
 * answerIndex와 explanation이 포함되어 있으므로 생성 응답에는 절대 사용하지 않는다.
 * correct: 미제출 상태면 null(아직 채점 불가), 제출 후엔 true/false.
 */
@Getter
@RequiredArgsConstructor
public class QuizResultItem {

    private final Long questionId;
    private final int questionOrder;
    private final String question;
    private final List<String> choices;
    private final int answerIndex;
    private final Integer userAnswerIndex;
    private final Boolean correct;
    private final String explanation;

    public static QuizResultItem from(QuizQuestion q) {
        Boolean correct = q.getUserAnswerIndex() != null ? q.isCorrect() : null;
        return new QuizResultItem(
                q.getId(),
                q.getQuestionOrder(),
                q.getQuestion(),
                q.getChoices(),
                q.getAnswerIndex(),
                q.getUserAnswerIndex(),
                correct,
                q.getExplanation()
        );
    }
}
