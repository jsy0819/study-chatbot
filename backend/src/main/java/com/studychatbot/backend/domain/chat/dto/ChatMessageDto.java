package com.studychatbot.backend.domain.chat.dto;

import com.studychatbot.backend.domain.chat.entity.ChatMessage;
import com.studychatbot.backend.domain.chat.entity.MessageRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ChatMessageDto {

    private final MessageRole role;
    private final String content;
    private final int messageOrder;

    public static ChatMessageDto from(ChatMessage message) {
        return new ChatMessageDto(
                message.getRole(),
                message.getContent(),
                message.getMessageOrder()
        );
    }
}
