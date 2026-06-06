package com.studychatbot.backend.domain.auth.dto;

import com.studychatbot.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SignupResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final LocalDateTime createdAt;

    public static SignupResponse from(User user) {
        return SignupResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
