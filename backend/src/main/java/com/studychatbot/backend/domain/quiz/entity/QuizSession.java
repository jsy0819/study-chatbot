package com.studychatbot.backend.domain.quiz.entity;

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
@Table(name = "quiz_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    // 제출 전 null, 채점 후 맞은 개수로 채워짐
    @Column
    private Integer score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 제출 전 null, 제출 시점 타임스탬프로 채워짐 — submittedAt != null이 제출 완료 상태
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    // cascade=ALL: 세션 저장/삭제 시 질문들도 함께 처리
    // orphanRemoval: 컬렉션에서 제거된 질문은 DB에서도 삭제
    @OneToMany(mappedBy = "quizSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("questionOrder ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    @Builder
    private QuizSession(User user, Document document, int questionCount) {
        this.user = user;
        this.document = document;
        this.questionCount = questionCount;
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    public void submit(int score, LocalDateTime submittedAt) {
        this.score = score;
        this.submittedAt = submittedAt;
    }
}
