package com.studychatbot.backend.domain.chat.service;

import com.studychatbot.backend.domain.chat.dto.ChatRequest;
import com.studychatbot.backend.domain.chat.dto.ChatResponse;
import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.document.repository.DocumentRepository;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.exception.DocumentNotFoundException;
import com.studychatbot.backend.global.exception.ForbiddenException;
import com.studychatbot.backend.global.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int TOP_K = 4;

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatResponse chat(String email, ChatRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(DocumentNotFoundException::new);

        // 본인 자료인지 검증 (DocumentService와 동일한 소유권 검사 패턴)
        if (!document.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        // FilterExpressionBuilder: 문자열 파서 우회, Long → String 명시 변환으로 TAG 타입 일치 보장
        var filter = new FilterExpressionBuilder()
                .eq("documentId", String.valueOf(request.getDocumentId()))
                .build();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getMessage())
                .topK(TOP_K)
                .filterExpression(filter)
                .similarityThreshold(0.0)  // 임계값 필터링 배제 — 청크 품질과 무관하게 topK 반환
                .build();

        List<org.springframework.ai.document.Document> chunks =
                vectorStore.similaritySearch(searchRequest);

        log.info("RAG 검색 완료 — documentId={}, 검색된 청크 수={}", request.getDocumentId(), chunks.size());

        if (chunks.isEmpty()) {
            return new ChatResponse("해당 자료에서 질문과 관련된 내용을 찾지 못했습니다. 다른 질문을 해보세요.");
        }

        String context = chunks.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildRagPrompt(context, request.getMessage());
        String answer = chatClient.prompt().user(prompt).call().content();
        return new ChatResponse(answer);
    }

    private String buildRagPrompt(String context, String question) {
        return """
                다음 학습 자료 내용을 참고해서 질문에 답해주세요.
                자료에 없는 내용은 모른다고 답하세요.

                [참고 자료]
                %s

                [질문]
                %s
                """.formatted(context, question);
    }
}
