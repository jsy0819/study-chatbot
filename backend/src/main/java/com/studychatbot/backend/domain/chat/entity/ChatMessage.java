package com.studychatbot.backend.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관관계 주인 — chat_session_id FK를 보유
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 세션 내 메시지 순서(1부터). USER/AI가 번갈아 쌓이며 조회 시 정렬 기준이 된다.
    @Column(name = "message_order", nullable = false)
    private int messageOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatMessage(ChatSession chatSession, MessageRole role, String content, int messageOrder) {
        this.chatSession = chatSession;
        this.role = role;
        this.content = content;
        this.messageOrder = messageOrder;
    }
}
