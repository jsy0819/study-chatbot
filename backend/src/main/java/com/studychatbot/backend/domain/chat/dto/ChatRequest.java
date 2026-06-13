package com.studychatbot.backend.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ChatRequest {

    @NotNull(message = "자료 ID는 필수입니다.")
    private Long documentId;

    @NotBlank(message = "질문을 입력해주세요.")
    private String message;

    // 선택: 값이 있으면 기존 세션에 이어쓰기, 없으면(null) 새 세션을 생성한다.
    private Long sessionId;
}
