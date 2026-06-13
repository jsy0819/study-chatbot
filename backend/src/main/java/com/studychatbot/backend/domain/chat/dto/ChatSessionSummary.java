package com.studychatbot.backend.domain.chat.dto;

import com.studychatbot.backend.domain.chat.entity.ChatSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class ChatSessionSummary {

    private final Long sessionId;
    private final String documentTitle;
    private final String title;
    private final long messageCount;
    private final LocalDateTime createdAt;

    public static ChatSessionSummary from(ChatSession session, long messageCount) {
        return new ChatSessionSummary(
                session.getId(),
                session.getDocument().getTitle(),
                session.getTitle(),
                messageCount,
                session.getCreatedAt()
        );
    }
}
