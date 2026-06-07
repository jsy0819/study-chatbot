package com.studychatbot.backend.domain.auth.controller;

import com.studychatbot.backend.domain.auth.dto.LoginRequest;
import com.studychatbot.backend.domain.auth.dto.LoginResponse;
import com.studychatbot.backend.domain.auth.dto.SignupRequest;
import com.studychatbot.backend.domain.auth.dto.SignupResponse;
import com.studychatbot.backend.domain.auth.dto.TokenRefreshRequest;
import com.studychatbot.backend.domain.auth.dto.TokenRefreshResponse;
import com.studychatbot.backend.domain.auth.service.AuthService;
import com.studychatbot.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@RequestBody @Valid SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @RequestBody @Valid TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    // 액세스 토큰으로 본인 확인 후 Redis의 리프레시 토큰 삭제
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(ApiResponse.of("로그아웃되었습니다."));
    }
}
