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
}
