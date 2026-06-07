package com.studychatbot.backend.domain.auth.service;

import com.studychatbot.backend.domain.auth.dto.LoginRequest;
import com.studychatbot.backend.domain.auth.dto.LoginResponse;
import com.studychatbot.backend.domain.auth.dto.SignupRequest;
import com.studychatbot.backend.domain.auth.dto.SignupResponse;
import com.studychatbot.backend.domain.auth.dto.TokenRefreshRequest;
import com.studychatbot.backend.domain.auth.dto.TokenRefreshResponse;
import com.studychatbot.backend.domain.auth.repository.RefreshTokenRepository;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.exception.DuplicateEmailException;
import com.studychatbot.backend.global.exception.InvalidCredentialsException;
import com.studychatbot.backend.global.exception.InvalidTokenException;
import com.studychatbot.backend.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException();
        }

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .name(request.getName())
            .build();

        return SignupResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(InvalidCredentialsException::new);

        // passwordHash가 null이면 소셜 전용 계정 — 비밀번호 로그인 불가
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        // Redis에 저장 — TTL은 리프레시 토큰 만료시간과 동일하게 설정해 자동 삭제
        refreshTokenRepository.save(user.getEmail(), refreshToken, jwtUtil.getRefreshExpirationMs());

        return LoginResponse.of(accessToken, refreshToken);
    }

    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new InvalidTokenException();
        }

        String email = jwtUtil.extractEmail(refreshToken);

        // Redis에 저장된 토큰과 일치하는지 확인 — 탈취 또는 이미 로그아웃된 토큰 거부
        String stored = refreshTokenRepository.find(email)
            .orElseThrow(InvalidTokenException::new);

        if (!stored.equals(refreshToken)) {
            throw new InvalidTokenException();
        }

        return TokenRefreshResponse.of(jwtUtil.generateAccessToken(email));
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isTokenValid(token)) {
            throw new InvalidTokenException();
        }

        String email = jwtUtil.extractEmail(token);
        refreshTokenRepository.delete(email);
    }
}
