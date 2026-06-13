package com.studychatbot.backend.domain.chat.service;

import com.studychatbot.backend.domain.chat.dto.ChatRequest;
import com.studychatbot.backend.domain.chat.dto.ChatSessionDetailResponse;
import com.studychatbot.backend.domain.chat.dto.ChatSessionSummary;
import com.studychatbot.backend.domain.chat.entity.ChatMessage;
import com.studychatbot.backend.domain.chat.entity.ChatSession;
import com.studychatbot.backend.domain.chat.entity.MessageRole;
import com.studychatbot.backend.domain.chat.repository.ChatMessageRepository;
import com.studychatbot.backend.domain.chat.repository.ChatSessionRepository;
import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.document.repository.DocumentRepository;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.exception.ChatSessionNotFoundException;
import com.studychatbot.backend.global.exception.DocumentNotFoundException;
import com.studychatbot.backend.global.exception.ForbiddenException;
import com.studychatbot.backend.global.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 채팅 기록 영속화 전담 서비스.
 *
 * 설계 결정: RAG·LLM 호출(ChatService)과 분리한 이유 —
 * 스트리밍 완료 콜백(doOnComplete)은 컨트롤러 트랜잭션 밖의 비동기 스레드에서 실행된다.
 * 같은 빈에서 @Transactional 메서드를 self-invocation하면 Spring AOP 프록시를 우회해
 * 트랜잭션이 적용되지 않으므로, 영속화를 별도 빈으로 분리해 외부 호출(프록시 경유)이 되게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatHistoryService {

    // 세션 제목은 첫 질문 내용으로 설정하되 과도하게 길지 않게 자른다.
    private static final int MAX_TITLE_LENGTH = 50;

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /** 세션 확보 결과. RAG 검색은 세션에 묶인 문서를 대상으로 하므로 documentId를 함께 전달한다. */
    public record PreparedSession(Long sessionId, Long documentId) {}

    /**
     * 질문 처리 전 호출. 세션을 확보(이어쓰기/신규 생성)하고 USER 메시지를 저장한다.
     * sessionId가 있으면 그 세션에 이어쓰고, 없으면 새 세션을 만든다(제목=첫 질문).
     */
    @Transactional
    public PreparedSession prepareSession(String email, ChatRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        ChatSession session;
        if (request.getSessionId() != null) {
            session = chatSessionRepository.findById(request.getSessionId())
                    .orElseThrow(ChatSessionNotFoundException::new);
            if (!session.getUser().getId().equals(user.getId())) {
                throw new ForbiddenException();
            }
            // 이어쓰기 시 RAG 대상 문서는 세션에 묶인 문서로 고정(요청의 documentId가 아니라).
        } else {
            Document document = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(DocumentNotFoundException::new);
            if (!document.getUser().getId().equals(user.getId())) {
                throw new ForbiddenException();
            }
            session = chatSessionRepository.save(ChatSession.builder()
                    .user(user)
                    .document(document)
                    .title(buildTitle(request.getMessage()))
                    .build());
            log.info("채팅 세션 생성 — sessionId={}, documentId={}", session.getId(), document.getId());
        }

        saveMessage(session, MessageRole.USER, request.getMessage());

        return new PreparedSession(session.getId(), session.getDocument().getId());
    }

    /**
     * AI 답변 메시지를 저장한다. 동기 채팅은 답변 생성 직후,
     * 스트리밍 채팅은 스트림 정상 완료 콜백(비동기 스레드)에서 호출된다.
     */
    @Transactional
    public void saveAiMessage(Long sessionId, String content) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(ChatSessionNotFoundException::new);
        saveMessage(session, MessageRole.AI, content);
    }

    /** ChatMessage가 연관관계 주인이므로 직접 저장한다(증분 저장). order는 기존 메시지 수+1. */
    private void saveMessage(ChatSession session, MessageRole role, String content) {
        int order = (int) chatMessageRepository.countByChatSession(session) + 1;
        chatMessageRepository.save(ChatMessage.builder()
                .chatSession(session)
                .role(role)
                .content(content)
                .messageOrder(order)
                .build());
    }

    private String buildTitle(String message) {
        String trimmed = message.strip();
        return trimmed.length() > MAX_TITLE_LENGTH
                ? trimmed.substring(0, MAX_TITLE_LENGTH)
                : trimmed;
    }

    /** 본인의 채팅 세션 목록을 최신순으로 반환한다(메시지 수 포함). */
    public List<ChatSessionSummary> getMySessions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        return chatSessionRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(session -> ChatSessionSummary.from(
                        session, chatMessageRepository.countByChatSession(session)))
                .toList();
    }

    /** 특정 세션의 상세(메시지 목록)를 반환한다. 소유권 검증 후 공개. */
    public ChatSessionDetailResponse getSessionDetail(String email, Long sessionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(ChatSessionNotFoundException::new);

        if (!session.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        return ChatSessionDetailResponse.from(session);
    }

    /** 세션 삭제. cascade=ALL + orphanRemoval로 소속 메시지도 함께 삭제된다. */
    @Transactional
    public void deleteSession(String email, Long sessionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(ChatSessionNotFoundException::new);

        if (!session.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        chatSessionRepository.delete(session);
        log.info("채팅 세션 삭제 — sessionId={}", sessionId);
    }
}
