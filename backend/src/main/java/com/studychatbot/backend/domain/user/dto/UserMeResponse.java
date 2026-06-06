package com.studychatbot.backend.domain.user.dto;

import com.studychatbot.backend.domain.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserMeResponse {

    private final Long id;
    private final String email;
    private final String name;

    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getEmail(), user.getName());
    }
}
