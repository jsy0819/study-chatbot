package com.studychatbot.backend.global.exception;

public class QuizSessionNotFoundException extends RuntimeException {

    public QuizSessionNotFoundException() {
        super("존재하지 않는 퀴즈 세션입니다.");
    }
}
