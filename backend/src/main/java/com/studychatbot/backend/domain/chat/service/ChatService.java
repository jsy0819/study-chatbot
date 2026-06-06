package com.studychatbot.backend.domain.chat.service;

import com.studychatbot.backend.domain.chat.dto.ChatRequest;
import com.studychatbot.backend.domain.chat.dto.ChatResponse;
import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.document.repository.DocumentRepository;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.client.GeminiClient;
import com.studychatbot.backend.global.exception.DocumentNotFoundException;
import com.studychatbot.backend.global.exception.ForbiddenException;
import com.studychatbot.backend.global.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final GeminiClient geminiClient;

    public ChatResponse chat(String email, ChatRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(DocumentNotFoundException::new);

        // 본인 자료인지 검증 (DocumentService와 동일한 소유권 검사 패턴)
        if (!document.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        String prompt = buildPrompt(document.getContent(), request.getMessage());
        String answer = geminiClient.chat(prompt);
        return new ChatResponse(answer);
    }

    private String buildPrompt(String documentContent, String question) {
        return """
                다음 학습 자료를 바탕으로 질문에 답해주세요.

                [자료]
                %s

                [질문]
                %s
                """.formatted(documentContent, question);
    }
}
