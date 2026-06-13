package com.studychatbot.backend.domain.chat.entity;

/**
 * 채팅 메시지 작성 주체.
 * USER: 사용자가 입력한 질문, AI: LLM이 생성한 답변.
 */
public enum MessageRole {
    USER,
    AI
}
