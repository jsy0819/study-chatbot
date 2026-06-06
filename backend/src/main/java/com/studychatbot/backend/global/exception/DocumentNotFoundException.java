package com.studychatbot.backend.global.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException() {
        super("존재하지 않는 자료입니다.");
    }
}
