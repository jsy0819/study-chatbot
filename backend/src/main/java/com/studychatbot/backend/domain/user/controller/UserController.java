package com.studychatbot.backend.domain.user.controller;

import com.studychatbot.backend.domain.user.dto.UserMeResponse;
import com.studychatbot.backend.domain.user.service.UserService;
import com.studychatbot.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(Authentication authentication) {
        String email = authentication.getName();
        UserMeResponse response = userService.getMe(email);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
