package com.studychatbot.backend.domain.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class QuizAnswerItem {

    @NotNull(message = "questionId는 필수입니다.")
    private Long questionId;

    @Min(value = 0, message = "selectedIndex는 0 이상이어야 합니다.")
    @Max(value = 3, message = "selectedIndex는 3 이하여야 합니다.")
    private int selectedIndex;
}
