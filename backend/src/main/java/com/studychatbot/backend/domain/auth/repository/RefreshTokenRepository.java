package com.studychatbot.backend.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String email, String refreshToken, long ttlMs) {
        redisTemplate.opsForValue().set(key(email), refreshToken, ttlMs, TimeUnit.MILLISECONDS);
    }

    public Optional<String> find(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(email)));
    }

    public void delete(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }
}
