package com.studychatbot.backend.global.exception;

public class QuizAlreadySubmittedException extends RuntimeException {

    public QuizAlreadySubmittedException() {
        super("이미 제출된 퀴즈 세션입니다.");
    }
}
