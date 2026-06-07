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
import com.studychatbot.backend.global.pdf.PdfTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PdfTextExtractor pdfTextExtractor;

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

    @Transactional
    public DocumentResponse uploadPdf(String email, MultipartFile file, String title) {
        User user = findUserByEmail(email);

        String extractedText = pdfTextExtractor.extract(file);
        String documentTitle = (title != null && !title.isBlank())
                ? title
                : stripExtension(file.getOriginalFilename());

        Document document = Document.builder()
                .user(user)
                .title(documentTitle)
                .content(extractedText)
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

    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "제목 없음";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
