package com.studychatbot.backend.domain.auth.service;

import com.studychatbot.backend.domain.auth.dto.LoginRequest;
import com.studychatbot.backend.domain.auth.dto.LoginResponse;
import com.studychatbot.backend.domain.auth.dto.SignupRequest;
import com.studychatbot.backend.domain.auth.dto.SignupResponse;
import com.studychatbot.backend.domain.user.entity.User;
import com.studychatbot.backend.domain.user.repository.UserRepository;
import com.studychatbot.backend.global.exception.DuplicateEmailException;
import com.studychatbot.backend.global.exception.InvalidCredentialsException;
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

        return LoginResponse.of(jwtUtil.generateToken(user.getEmail()));
    }
}
