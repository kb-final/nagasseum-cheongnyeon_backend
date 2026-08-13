package com.team.independence.goal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.goal.dto.MonteCarloResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 목표별 Monte Carlo 시뮬레이션 결과를 Redis에 캐싱한다.
 * Key: goal:simulation:{goalId} (TTL: 30일)
 *
 * 시뮬레이션 결과는 목표 조건(주거 조건·목표 시점·월 저축액)이 바뀌지 않는 한 재계산할 필요가 없다.
 * 목표 수정 및 삭제(ARCHIVE) 시 {@link #delete}로 무효화하면 다음 조회 때 새 조건으로 다시 계산된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonteCarloSimulationStore {

    private static final String KEY_PREFIX = "goal:simulation:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<MonteCarloResponse> find(Long goalId) {
        String json = redisTemplate.opsForValue().get(key(goalId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MonteCarloResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("[Monte Carlo 캐시] 역직렬화 실패, 캐시 미스로 처리: goalId={}", goalId, e);
            return Optional.empty();
        }
    }

    public void save(Long goalId, MonteCarloResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(goalId), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("[Monte Carlo 캐시] 직렬화 실패, 캐싱 건너뜀: goalId={}", goalId, e);
        }
    }

    /**
     * 목표 조건 수정 및 삭제 시 바뀌면 캐시를 무효화한다.
     * 다음 조회 때 새 조건으로 재계산된다.
     */
    public void delete(Long goalId) {
        redisTemplate.delete(key(goalId));
    }

    private String key(Long goalId) {
        return KEY_PREFIX + goalId;
    }
}
