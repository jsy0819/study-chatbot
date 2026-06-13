package com.studychatbot.backend.domain.chat.service;

import com.studychatbot.backend.domain.chat.dto.ChatRequest;
import com.studychatbot.backend.domain.chat.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TOP_K = 4;
    private static final String NO_CONTEXT_MESSAGE =
            "해당 자료에서 질문과 관련된 내용을 찾지 못했습니다. 다른 질문을 해보세요.";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatHistoryService chatHistoryService;

    /** 스트리밍 응답과 함께, 프론트가 이어쓰기에 쓸 sessionId를 전달한다. */
    public record ChatStream(Long sessionId, Flux<String> tokens) {}

    public ChatResponse chat(String email, ChatRequest request) {
        // 세션 확보(없으면 생성) + USER 메시지 저장. 소유권 검증도 여기서 수행.
        var prepared = chatHistoryService.prepareSession(email, request);

        List<org.springframework.ai.document.Document> chunks =
                searchChunks(prepared.documentId(), request.getMessage());

        if (chunks.isEmpty()) {
            chatHistoryService.saveAiMessage(prepared.sessionId(), NO_CONTEXT_MESSAGE);
            return new ChatResponse(prepared.sessionId(), NO_CONTEXT_MESSAGE);
        }

        String context = joinContext(chunks);
        String prompt = buildRagPrompt(context, request.getMessage());
        String answer = chatClient.prompt().user(prompt).call().content();

        // LLM 호출 성공 시에만 도달 — 실패 시 예외가 전파되어 AI 메시지는 저장되지 않는다.
        chatHistoryService.saveAiMessage(prepared.sessionId(), answer);
        return new ChatResponse(prepared.sessionId(), answer);
    }

    /**
     * RAG 검색·검증은 동기로 수행하고, LLM 응답만 Flux로 스트리밍한다.
     * 스트림 시작 전 세션 확보 + USER 메시지 저장을 끝내고, 토큰을 누적했다가
     * 스트림 정상 완료 시 누적 답변을 AI 메시지로 저장한다.
     *
     * 예외(ForbiddenException, DocumentNotFoundException 등)는 Flux 생성 전에 던져지므로
     * GlobalExceptionHandler가 일반 HTTP 오류 응답으로 처리한다.
     */
    public ChatStream streamAnswer(String email, ChatRequest request) {
        var prepared = chatHistoryService.prepareSession(email, request);
        Long sessionId = prepared.sessionId();
        Long documentId = prepared.documentId();

        List<org.springframework.ai.document.Document> chunks =
                searchChunks(documentId, request.getMessage());

        if (chunks.isEmpty()) {
            chatHistoryService.saveAiMessage(sessionId, NO_CONTEXT_MESSAGE);
            return new ChatStream(sessionId, Flux.just(NO_CONTEXT_MESSAGE));
        }

        String context = joinContext(chunks);
        String prompt = buildRagPrompt(context, request.getMessage());

        // 토큰을 흘려보내며 서버에서 누적. 정상 완료 시에만 저장한다.
        StringBuilder accumulated = new StringBuilder();
        AtomicBoolean failed = new AtomicBoolean(false);

        Flux<String> tokens = chatClient.prompt().user(prompt).stream().content()
                // 실제 LLM 토큰만 누적(아래 onErrorResume의 안내 토큰은 누적되지 않는다)
                .doOnNext(accumulated::append)
                // LLM 호출이 스트림 도중 실패(예: Gemini 429 Quota)하면 Flux가 error로 종료된다.
                // 그대로 두면 SseEmitter가 completeWithError로 끊겨 클라이언트엔 깨진 청크로 보인다.
                // 에러를 사용자용 안내 메시지(정상 토큰)로 치환해 스트림을 정상 완료시킨다.
                .onErrorResume(e -> {
                    failed.set(true);
                    log.error("LLM 스트리밍 실패 — documentId={}", documentId, e);
                    return Flux.just(toUserFacingError(e));
                })
                // 비동기 완료 시점은 컨트롤러 트랜잭션 밖이므로, 별도 빈의 @Transactional 메서드로 저장한다.
                // 에러 시(failed)엔 부분 답변을 저장하지 않는다(설계 결정).
                .doOnComplete(() -> {
                    if (!failed.get() && accumulated.length() > 0) {
                        chatHistoryService.saveAiMessage(sessionId, accumulated.toString());
                    }
                });

        return new ChatStream(sessionId, tokens);
    }

    /**
     * 자료(documentId)에 대해 질문과 관련된 청크를 벡터 검색한다.
     * FilterExpressionBuilder: 문자열 파서 우회, Long → String 명시 변환으로 TAG 타입 일치 보장.
     */
    private List<org.springframework.ai.document.Document> searchChunks(Long documentId, String query) {
        var filter = new FilterExpressionBuilder()
                .eq("documentId", String.valueOf(documentId))
                .build();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(TOP_K)
                .filterExpression(filter)
                .similarityThreshold(0.0)  // 임계값 필터링 배제 — 청크 품질과 무관하게 topK 반환
                .build();

        List<org.springframework.ai.document.Document> chunks =
                vectorStore.similaritySearch(searchRequest);
        log.info("RAG 검색 완료 — documentId={}, 검색된 청크 수={}", documentId, chunks.size());
        return chunks;
    }

    private String joinContext(List<org.springframework.ai.document.Document> chunks) {
        return chunks.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));
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
