package com.studychatbot.backend.domain.quiz.dto;

import com.studychatbot.backend.global.validation.ValidQuestionCount;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class QuizGenerationRequest {

    @NotNull(message = "자료 ID는 필수입니다.")
    private Long documentId;

    @NotNull(message = "questionCount는 필수입니다.")
    @ValidQuestionCount
    private Integer questionCount;
}
