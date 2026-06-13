package com.studychatbot.backend.domain.chat.repository;

import com.studychatbot.backend.domain.chat.entity.ChatMessage;
import com.studychatbot.backend.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 다음 messageOrder 계산 및 세션 요약의 메시지 수 표시에 사용
    long countByChatSession(ChatSession chatSession);
}
