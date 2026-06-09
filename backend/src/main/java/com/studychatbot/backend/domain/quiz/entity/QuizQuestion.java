package com.studychatbot.backend.domain.quiz.entity;

import com.studychatbot.backend.global.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "quiz_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관관계 주인 — quiz_session_id FK를 보유
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_session_id", nullable = false)
    private QuizSession quizSession;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    // List<String> → JSON 문자열("["보기1","보기2","보기3","보기4"]")로 단일 컬럼에 저장
    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> choices;

    // 정답 인덱스 — 생성 응답 DTO에 절대 포함하지 않는다
    @Column(name = "answer_index", nullable = false)
    private int answerIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    // 제출 전 null, 사용자 답 제출 후 채워짐
    @Column(name = "user_answer_index")
    private Integer userAnswerIndex;

    @Builder
    private QuizQuestion(QuizSession quizSession, int questionOrder, String question,
                         List<String> choices, int answerIndex, String explanation) {
        this.quizSession = quizSession;
        this.questionOrder = questionOrder;
        this.question = question;
        this.choices = choices;
        this.answerIndex = answerIndex;
        this.explanation = explanation;
    }

    public void submitAnswer(int selectedIndex) {
        this.userAnswerIndex = selectedIndex;
    }

    /** userAnswerIndex가 null이면(미제출) false를 반환한다 */
    public boolean isCorrect() {
        return userAnswerIndex != null && userAnswerIndex == answerIndex;
    }
}
