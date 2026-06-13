package com.studychatbot.backend.global.exception;

public class ChatSessionNotFoundException extends RuntimeException {

    public ChatSessionNotFoundException() {
        super("존재하지 않는 채팅 세션입니다.");
    }
}
