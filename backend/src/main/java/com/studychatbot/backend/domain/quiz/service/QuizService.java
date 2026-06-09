package com.studychatbot.backend.domain.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.document.repository.DocumentRepository;
import com.studychatbot.backend.domain.quiz.dto.*;
import com.studychatbot.backend.domain.quiz.entity.QuizQuestion;
import com.studychatbot.backend.domain.quiz.entity.QuizSession;
import com.studychatbot.backend.domain.quiz.repository.QuizSessionRepository;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    /**
     * LLM으로 퀴즈를 생성하고 세션·질문을 DB에 저장한다.
     * 응답 DTO에는 answerIndex·explanation이 없다 — 정답 숨김 보장.
     */
    @Transactional
    public QuizSessionCreateResponse generate(String email, QuizGenerationRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(DocumentNotFoundException::new);

        if (!document.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        // questionCount에 비례해 topK 조정 — 많은 문제일수록 더 넓은 맥락 필요
        int topK = Math.min(request.getQuestionCount() * 2, 10);
        var filter = new FilterExpressionBuilder()
                .eq("documentId", String.valueOf(request.getDocumentId()))
                .build();
        SearchRequest searchRequest = SearchRequest.builder()
                .query("핵심 개념 주요 용어 중요 내용")
                .topK(topK)
                .filterExpression(filter)
                .similarityThreshold(0.0)
                .build();

        List<org.springframework.ai.document.Document> chunks =
                vectorStore.similaritySearch(searchRequest);

        log.info("퀴즈 RAG 검색 완료 — documentId={}, 청크 수={}", request.getDocumentId(), chunks.size());

        if (chunks.isEmpty()) {
            throw new QuizParsingException("해당 자료에서 내용을 찾을 수 없습니다. 자료가 아직 처리 중이거나 내용이 없을 수 있습니다.");
        }

        String context = chunks.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildQuizPrompt(context, request.getQuestionCount());
        String raw = chatClient.prompt().user(prompt).call().content();

        log.debug("LLM 퀴즈 원문 응답 — documentId={}, length={}", request.getDocumentId(), raw.length());

        List<QuizItemDto> items = parseAndValidate(raw, request.getQuestionCount());

        // QuizSession 생성 후 QuizQuestion들을 cascade로 함께 저장
        QuizSession session = QuizSession.builder()
                .user(user)
                .document(document)
                .questionCount(items.size())
                .build();

        for (int i = 0; i < items.size(); i++) {
            QuizItemDto item = items.get(i);
            QuizQuestion question = QuizQuestion.builder()
                    .quizSession(session)
                    .questionOrder(i + 1)
                    .question(item.getQuestion())
                    .choices(item.getChoices())
                    .answerIndex(item.getAnswerIndex())
                    .explanation(item.getExplanation())
                    .build();
            session.getQuestions().add(question);
        }

        QuizSession saved = quizSessionRepository.save(session);
        log.info("퀴즈 세션 저장 완료 — sessionId={}, 문제 수={}", saved.getId(), saved.getQuestions().size());

        return QuizSessionCreateResponse.from(saved);
    }

    /**
     * 사용자 답안을 받아 채점하고 결과를 반환한다.
     * 정답이 이 시점에 처음으로 클라이언트에 공개된다.
     */
    @Transactional
    public QuizSubmitResponse submit(String email, Long sessionId, QuizSubmitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        QuizSession session = quizSessionRepository.findById(sessionId)
                .orElseThrow(QuizSessionNotFoundException::new);

        if (!session.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        if (session.isSubmitted()) {
            throw new QuizAlreadySubmittedException();
        }

        // questionId → QuizQuestion 맵으로 O(1) 조회
        Map<Long, QuizQuestion> questionMap = session.getQuestions().stream()
                .collect(Collectors.toMap(QuizQuestion::getId, q -> q));

        for (QuizAnswerItem answer : request.getAnswers()) {
            QuizQuestion q = questionMap.get(answer.getQuestionId());
            if (q != null) {
                q.submitAnswer(answer.getSelectedIndex());
            }
        }

        int score = (int) session.getQuestions().stream()
                .filter(QuizQuestion::isCorrect)
                .count();

        session.submit(score, LocalDateTime.now());
        // @Transactional dirty checking으로 save() 호출 없이 변경 사항이 반영된다

        log.info("퀴즈 채점 완료 — sessionId={}, score={}/{}", sessionId, score, session.getQuestionCount());

        return QuizSubmitResponse.from(session);
    }

    /** 본인의 퀴즈 세션 목록을 최신순으로 반환한다. */
    public List<QuizSessionSummary> getMySessions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        return quizSessionRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(QuizSessionSummary::from)
                .toList();
    }

    /** 특정 세션의 상세 정보를 반환한다. 소유권 검증 후 정답·해설 포함해 공개. */
    public QuizSessionDetailResponse getSessionDetail(String email, Long sessionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        QuizSession session = quizSessionRepository.findById(sessionId)
                .orElseThrow(QuizSessionNotFoundException::new);

        if (!session.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        return QuizSessionDetailResponse.from(session);
    }

    private List<QuizItemDto> parseAndValidate(String raw, int expectedCount) {
        String json = extractJson(raw);
        List<QuizItemDto> items;
        try {
            items = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("퀴즈 JSON 파싱 실패 — raw={}", raw, e);
            throw new QuizParsingException("LLM 응답을 퀴즈 형식으로 파싱할 수 없습니다. 다시 시도해주세요.");
        }

        for (int i = 0; i < items.size(); i++) {
            QuizItemDto item = items.get(i);
            if (item.getChoices() == null || item.getChoices().size() != 4) {
                throw new QuizParsingException(
                        (i + 1) + "번 문제의 보기가 4개가 아닙니다. 다시 시도해주세요.");
            }
            if (item.getAnswerIndex() < 0 || item.getAnswerIndex() > 3) {
                throw new QuizParsingException(
                        (i + 1) + "번 문제의 answerIndex(" + item.getAnswerIndex() + ")가 0~3 범위를 벗어납니다. 다시 시도해주세요.");
            }
        }

        log.info("퀴즈 파싱 완료 — 문제 수={} (요청={})", items.size(), expectedCount);
        return items;
    }

    /**
     * 코드펜스(```json...``` 또는 ```...```)가 섞인 응답에서 JSON 배열 부분만 추출한다.
     * 코드펜스 제거 → '[' ... ']' 범위 보정 순으로 처리해 앞뒤 군말을 걷어낸다.
     */
    private String extractJson(String raw) {
        String trimmed = raw.strip();

        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            int closeFence = trimmed.lastIndexOf("```");
            if (newline != -1 && closeFence > newline) {
                trimmed = trimmed.substring(newline, closeFence).strip();
            }
        }

        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    /**
     * %s/%d 대신 {count}/{context} 플레이스홀더 + replace()를 사용한다 —
     * 자료 내용에 '%' 기호가 포함되면 String.formatted()가 IllegalFormatException을 던지기 때문이다.
     */
    private String buildQuizPrompt(String context, int questionCount) {
        String template = """
                다음 학습 자료를 기반으로 4지선다 퀴즈 {count}개를 만들어주세요.

                [중요 출력 규칙 — 반드시 준수]
                - 아래 JSON 배열 형식만 출력하세요.
                - 설명 문장, 마크다운 코드펜스(```), 기타 어떤 텍스트도 절대 포함하지 마세요.
                - 첫 글자는 반드시 '[' 이어야 하고 마지막 글자는 반드시 ']' 이어야 합니다.

                [출력 형식]
                [
                  {
                    "question": "문제 내용",
                    "choices": ["보기1", "보기2", "보기3", "보기4"],
                    "answerIndex": 0,
                    "explanation": "해설 내용"
                  }
                ]

                [각 필드 규칙]
                - choices: 반드시 문자열 4개짜리 배열
                - answerIndex: 0~3 사이의 정수 (0이 choices[0]을 가리킴)
                - explanation: 왜 그 보기가 정답인지 간결하게 설명
                - 모든 문제는 아래 학습 자료 내용에서만 출제

                [학습 자료]
                {context}
                """;

        return template
                .replace("{count}", String.valueOf(questionCount))
                .replace("{context}", context);
    }
}
