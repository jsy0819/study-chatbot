package com.studychatbot.backend.domain.quiz.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class QuizGenerationResponse {

    private final List<QuizItemDto> quizzes;
}
