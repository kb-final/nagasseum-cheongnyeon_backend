package com.team.independence.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token을 Redis에 저장한다.
 * Key: refresh:member:{memberId}  Value: token  TTL: jwt.refresh-token-validity-ms
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:member:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-validity-ms}")
    private long refreshTokenValidityMs;

    public void save(Long memberId, String token) {
        redisTemplate.opsForValue().set(
                key(memberId), token,
                refreshTokenValidityMs, TimeUnit.MILLISECONDS
        );
    }

    public Optional<String> find(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(memberId)));
    }

    public void delete(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}