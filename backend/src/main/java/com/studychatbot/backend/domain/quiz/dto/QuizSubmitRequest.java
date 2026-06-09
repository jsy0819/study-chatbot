package com.studychatbot.backend.domain.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public class QuizSubmitRequest {

    @NotEmpty(message = "답안이 비어 있습니다.")
    @Valid
    private List<QuizAnswerItem> answers;
}
