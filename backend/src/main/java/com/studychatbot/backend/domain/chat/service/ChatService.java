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
import reactor.core.publisher.Flux;

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

    /**
     * RAG 검색·검증은 동기로 수행하고, LLM 응답만 Flux로 스트리밍한다.
     * 예외(ForbiddenException, DocumentNotFoundException 등)는 Flux 생성 전에 던져지므로
     * GlobalExceptionHandler가 일반 HTTP 오류 응답으로 처리한다.
     */
    public Flux<String> streamAnswer(String email, ChatRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(DocumentNotFoundException::new);

        if (!document.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        var filter = new FilterExpressionBuilder()
                .eq("documentId", String.valueOf(request.getDocumentId()))
                .build();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getMessage())
                .topK(TOP_K)
                .filterExpression(filter)
                .similarityThreshold(0.0)
                .build();

        List<org.springframework.ai.document.Document> chunks =
                vectorStore.similaritySearch(searchRequest);

        log.info("RAG 스트리밍 검색 완료 — documentId={}, 검색된 청크 수={}", request.getDocumentId(), chunks.size());

        if (chunks.isEmpty()) {
            return Flux.just("해당 자료에서 질문과 관련된 내용을 찾지 못했습니다. 다른 질문을 해보세요.");
        }

        String context = chunks.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildRagPrompt(context, request.getMessage());
        return chatClient.prompt().user(prompt).stream().content()
                // LLM 호출이 스트림 도중 실패(예: Gemini 429 Quota)하면 Flux가 error로 종료된다.
                // 그대로 두면 SseEmitter가 completeWithError로 끊겨 클라이언트엔 깨진 청크로 보인다.
                // 에러를 사용자용 안내 메시지(정상 토큰)로 치환해 스트림을 정상 완료시킨다.
                .onErrorResume(e -> {
                    log.error("LLM 스트리밍 실패 — documentId={}", request.getDocumentId(), e);
                    return Flux.just(toUserFacingError(e));
                });
    }

    /**
     * LLM 호출 예외를 사용자에게 보여줄 안내 메시지로 변환한다.
     * Gemini 무료 등급의 분당 요청 한도 초과(429 / RESOURCE_EXHAUSTED)는 별도 안내한다.
     * 앞에 줄바꿈을 둬, 이미 일부 답변이 출력된 경우에도 시각적으로 구분되게 한다.
     */
    private String toUserFacingError(Throwable e) {
        String message = String.valueOf(e.getMessage()).toLowerCase();
        if (message.contains("429") || message.contains("resource_exhausted") || message.contains("quota")) {
            return "\n\n[안내] 요청이 많아 잠시 후 다시 시도해주세요.";
        }
        return "\n\n[안내] 답변 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
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
