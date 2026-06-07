package com.studychatbot.backend.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenRefreshResponse {

    private final String accessToken;
    private final String tokenType;

    public static TokenRefreshResponse of(String accessToken) {
        return TokenRefreshResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .build();
    }
}
