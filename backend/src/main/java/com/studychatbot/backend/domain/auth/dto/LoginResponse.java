package com.studychatbot.backend.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private final String accessToken;
    private final String tokenType;

    public static LoginResponse of(String accessToken) {
        return LoginResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .build();
    }
}
