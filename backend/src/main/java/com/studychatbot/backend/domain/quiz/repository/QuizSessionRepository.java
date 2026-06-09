package com.studychatbot.backend.domain.quiz.repository;

import com.studychatbot.backend.domain.quiz.entity.QuizSession;
import com.studychatbot.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    List<QuizSession> findAllByUserOrderByCreatedAtDesc(User user);
}
