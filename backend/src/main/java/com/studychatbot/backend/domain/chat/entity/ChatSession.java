package com.studychatbot.backend.domain.chat.entity;

import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    // 첫 질문 내용으로 설정(너무 길면 잘라서) — 세션 목록에서 식별용
    @Column(nullable = false)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // cascade=ALL: 세션 저장/삭제 시 메시지들도 함께 처리
    // orphanRemoval: 컬렉션에서 제거된 메시지는 DB에서도 삭제 (세션 삭제 시 메시지 cascade 삭제)
    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("messageOrder ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    @Builder
    private ChatSession(User user, Document document, String title) {
        this.user = user;
        this.document = document;
        this.title = title;
    }
}
