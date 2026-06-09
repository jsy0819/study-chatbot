package com.studychatbot.backend.domain.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.document.repository.DocumentRepository;
import com.studychatbot.backend.domain.quiz.dto.QuizGenerationRequest;
import com.studychatbot.backend.domain.quiz.dto.QuizGenerationResponse;
import com.studychatbot.backend.domain.quiz.dto.QuizItemDto;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.exception.DocumentNotFoundException;
import com.studychatbot.backend.global.exception.ForbiddenException;
import com.studychatbot.backend.global.exception.InvalidCredentialsException;
import com.studychatbot.backend.global.exception.QuizParsingException;
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
public class QuizService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public QuizGenerationResponse generate(String email, QuizGenerationRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(DocumentNotFoundException::new);

        // DocumentService, ChatService와 동일한 소유권 검사 패턴
        if (!document.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException();
        }

        // 퀴즈 생성은 특정 질문 없이 전체 맥락이 필요 — 범용 쿼리로 핵심 청크를 넓게 수집
        // topK를 questionCount에 비례시켜 더 많은 문제일수록 더 다양한 자료 범위를 커버
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

        log.info("퀴즈 RAG 검색 완료 — documentId={}, 청크 수={}, topK={}", request.getDocumentId(), chunks.size(), topK);

        if (chunks.isEmpty()) {
            throw new QuizParsingException("해당 자료에서 내용을 찾을 수 없습니다. 자료가 아직 처리 중이거나 내용이 없을 수 있습니다.");
        }

        String context = chunks.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildQuizPrompt(context, request.getQuestionCount());
        String raw = chatClient.prompt().user(prompt).call().content();

        log.debug("LLM 퀴즈 원문 응답 — documentId={}, length={}", request.getDocumentId(), raw.length());

        List<QuizItemDto> quizzes = parseAndValidate(raw, request.getQuestionCount());
        return new QuizGenerationResponse(quizzes);
    }

    /**
     * LLM 응답에서 JSON 배열만 추출한 뒤 파싱하고, 각 문제의 형식을 검증한다.
     * LLM이 프롬프트를 어겨 코드펜스나 설명을 붙여도 배열 부분만 안전하게 꺼낼 수 있다.
     */
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

        // 코드펜스 제거 후에도 앞뒤에 설명이 붙어 있을 경우 배열 구간만 잘라낸다
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    /**
     * 학습 자료 기반 4지선다 퀴즈 생성 프롬프트.
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
