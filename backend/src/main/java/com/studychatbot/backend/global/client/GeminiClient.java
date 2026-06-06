package com.studychatbot.backend.global.client;

import com.studychatbot.backend.global.exception.GeminiApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Gemini generateContent REST API 호출 클라이언트.
 * Stage 2에서 Spring AI + RAG로 전환할 때 이 클래스만 교체하면 된다.
 */
@Slf4j
@Component
public class GeminiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            RestClient.Builder builder,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model}") String model,
            @Value("${gemini.api.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * 텍스트 프롬프트를 Gemini에 전송하고 응답 텍스트를 반환한다.
     */
    public String chat(String prompt) {
        GenerateRequest request = new GenerateRequest(
            List.of(new Content("user", List.of(new Part(prompt))))
        );

        try {
            GenerateResponse response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                // API 키를 헤더로 전달 — URL에 노출되면 서버 로그에 찍히므로 헤더 사용
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GenerateResponse.class);

            if (response == null
                    || response.candidates() == null
                    || response.candidates().isEmpty()) {
                throw new GeminiApiException("Gemini API 응답이 비어 있습니다.");
            }

            return response.candidates().get(0).content().parts().get(0).text();

        } catch (GeminiApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            throw new GeminiApiException("Gemini API 호출 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("Gemini API 예기치 못한 오류", e);
            throw new GeminiApiException("Gemini API 호출 중 오류가 발생했습니다.");
        }
    }

    // ── Gemini generateContent 요청/응답 구조 ──────────────────────────────
    // 요청: contents[].role + parts[].text
    record GenerateRequest(List<Content> contents) {}
    record Content(String role, List<Part> parts) {}
    record Part(String text) {}

    // 응답: candidates[].content.parts[].text
    record GenerateResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
}
