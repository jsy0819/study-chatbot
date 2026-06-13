package com.studychatbot.backend.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatResponse {
    // 프론트가 이어쓰기(같은 세션에 다음 질문)를 위해 알아야 하는 세션 식별자
    private Long sessionId;
    private String answer;
}
