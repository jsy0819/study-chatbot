package com.studychatbot.backend.domain.quiz.repository;

import com.studychatbot.backend.domain.quiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
}
