package com.studychatbot.backend.domain.chat.dto;

import com.studychatbot.backend.domain.chat.entity.ChatSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세션 상세 응답. 메시지 목록(role, content, order)을 순서대로 포함한다.
 */
@Getter
@RequiredArgsConstructor
public class ChatSessionDetailResponse {

    private final Long sessionId;
    private final String documentTitle;
    private final String title;
    private final LocalDateTime createdAt;
    private final List<ChatMessageDto> messages;

    public static ChatSessionDetailResponse from(ChatSession session) {
        List<ChatMessageDto> messages = session.getMessages().stream()
                .map(ChatMessageDto::from)
                .toList();
        return new ChatSessionDetailResponse(
                session.getId(),
                session.getDocument().getTitle(),
                session.getTitle(),
                session.getCreatedAt(),
                messages
        );
    }
}
