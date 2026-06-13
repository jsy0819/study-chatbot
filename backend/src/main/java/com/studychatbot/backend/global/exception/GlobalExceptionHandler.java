package com.studychatbot.backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> ErrorResponse.FieldError.builder()
                .field(error.getField())
                .message(error.getDefaultMessage())
                .build())
            .toList();

        return ResponseEntity.badRequest().body(
            ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("입력값이 올바르지 않습니다.")
                .errors(fieldErrors)
                .build()
        );
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse.builder()
                .code("DUPLICATE_EMAIL")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse.builder()
                .code("INVALID_CREDENTIALS")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse.builder()
                .code("DOCUMENT_NOT_FOUND")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse.builder()
                .code("FORBIDDEN")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse.builder()
                .code("INVALID_TOKEN")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(GeminiApiException.class)
    public ResponseEntity<ErrorResponse> handleGeminiApi(GeminiApiException e) {
        // 외부 LLM(Gemini) 호출 실패는 서버측에서 반드시 인지해야 하므로 로깅한다.
        // - warn 사용: GeminiClient가 이미 원인 상세를 log.error로 기록하므로 ERROR 이중 기록을 피하고,
        //   동시에 GeminiClient가 로깅하지 않는 "빈 응답" 경로까지 이 단일 지점에서 빠짐없이 포착한다.
        // - e.getMessage()에 어떤 외부 호출이 어떻게 실패했는지가 담겨 진단에 활용된다.
        log.warn("외부 LLM(Gemini) 호출 실패로 500 응답 — {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.builder()
                .code("GEMINI_API_ERROR")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(InvalidFileException e) {
        return ResponseEntity.badRequest().body(
            ErrorResponse.builder()
                .code("INVALID_FILE")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePdfProcessing(PdfProcessingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse.builder()
                .code("PDF_PROCESSING_ERROR")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ErrorResponse.builder()
                .code("FILE_TOO_LARGE")
                .message("파일 크기가 허용 한도(30MB)를 초과했습니다.")
                .build()
        );
    }

    @ExceptionHandler(QuizParsingException.class)
    public ResponseEntity<ErrorResponse> handleQuizParsing(QuizParsingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse.builder()
                .code("QUIZ_PARSING_ERROR")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(QuizSessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleQuizSessionNotFound(QuizSessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse.builder()
                .code("QUIZ_SESSION_NOT_FOUND")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException e) {
        // 한도 초과는 의도된 보호 동작이므로 스택트레이스 없이 warn 수준으로만 남긴다.
        // (남용/폭주 패턴 모니터링에는 충분한 단서가 된다.)
        log.warn("레이트리밋 초과 — {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
            ErrorResponse.builder()
                .code("RATE_LIMIT_EXCEEDED")
                .message(e.getMessage())
                .build()
        );
    }

    @ExceptionHandler(QuizAlreadySubmittedException.class)
    public ResponseEntity<ErrorResponse> handleQuizAlreadySubmitted(QuizAlreadySubmittedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse.builder()
                .code("QUIZ_ALREADY_SUBMITTED")
                .message(e.getMessage())
                .build()
        );
    }

    /**
     * 예상하지 못한 모든 예외에 대한 최종 fallback 핸들러.
     * (Redis/VectorStore 장애, ChatClient 실패, 기타 RuntimeException 등 위에서 명시적으로
     *  처리하지 않은 예외가 Spring 기본 /error로 빠지면서 통일된 에러 포맷을 벗어나고
     *  내부 정보가 노출되는 것을 막는다.)
     *
     * 설계 결정:
     * - 더 구체적인 @ExceptionHandler가 우선 매칭되므로, 위에서 처리하는 알려진 예외는
     *   이 fallback에 잡히지 않는다. (Spring의 ExceptionHandlerMethodResolver는 예외 계층상
     *   가장 가까운 핸들러를 선택한다.)
     * - 클라이언트에는 일반적인 메시지만 노출하고, 실제 예외 메시지·스택트레이스는 노출하지 않는다.
     * - 디버깅을 위해 서버 로그에는 스택트레이스를 포함해 실제 예외를 기록한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("서버 오류가 발생했습니다.")
                .build()
        );
    }
}
