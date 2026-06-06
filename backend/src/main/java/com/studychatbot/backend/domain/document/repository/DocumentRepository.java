package com.studychatbot.backend.domain.document.repository;

import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByUserOrderByCreatedAtDesc(User user);
}
