package com.studychatbot.backend.domain.quiz.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// @Setter: objectMapper.readValue()로 LLM 응답을 역직렬화할 때 Jackson이 setter를 통해 필드를 채운다
@Getter
@Setter
@NoArgsConstructor
public class QuizItemDto {

    private String question;
    private List<String> choices;
    private int answerIndex;
    private String explanation;
}
