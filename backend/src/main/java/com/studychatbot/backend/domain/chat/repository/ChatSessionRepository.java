package com.studychatbot.backend.domain.chat.repository;

import com.studychatbot.backend.domain.chat.entity.ChatSession;
import com.studychatbot.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findAllByUserOrderByCreatedAtDesc(User user);
}
