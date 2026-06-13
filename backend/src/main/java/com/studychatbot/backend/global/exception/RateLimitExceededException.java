package com.studychatbot.backend.global.exception;

/**
 * 사용자별 LLM 호출 한도(분당/일당)를 초과했을 때 던지는 예외.
 * GlobalExceptionHandler에서 429 Too Many Requests로 변환된다.
 * message에는 어떤 한도(분당/일당)를 초과했는지 안내 문구가 담긴다.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
