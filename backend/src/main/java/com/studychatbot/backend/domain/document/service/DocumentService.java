package com.studychatbot.backend.domain.document.service;

import com.studychatbot.backend.domain.document.dto.DocumentResponse;
import com.studychatbot.backend.domain.document.dto.DocumentUploadRequest;
import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.document.entity.DocumentStatus;
import com.studychatbot.backend.domain.document.repository.DocumentRepository;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.exception.DocumentNotFoundException;
import com.studychatbot.backend.global.exception.ForbiddenException;
import com.studychatbot.backend.global.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentResponse upload(String email, DocumentUploadRequest request) {
        User user = findUserByEmail(email);

        // 텍스트 기반 업로드는 즉시 처리 완료 상태로 저장 (PDF 추출은 2단계)
        Document document = Document.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .status(DocumentStatus.DONE)
                .build();

        return DocumentResponse.from(documentRepository.save(document));
    }

    public List<DocumentResponse> getMyDocuments(String email) {
        User user = findUserByEmail(email);
        return documentRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse getDocument(String email, Long id) {
        User user = findUserByEmail(email);
        Document document = findDocumentById(id);
        checkOwnership(document, user);
        return DocumentResponse.from(document);
    }

    @Transactional
    public void deleteDocument(String email, Long id) {
        User user = findUserByEmail(email);
        Document document = findDocumentById(id);
        checkOwnership(document, user);
        documentRepository.delete(document);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private Document findDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(DocumentNotFoundException::new);
    }

    private void checkOwnership(Document document, User user) {
        if (!document.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }
    }
}
