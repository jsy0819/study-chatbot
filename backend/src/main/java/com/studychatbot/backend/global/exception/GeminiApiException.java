package com.studychatbot.backend.global.exception;

public class GeminiApiException extends RuntimeException {

    public GeminiApiException(String message) {
        super(message);
    }
}
