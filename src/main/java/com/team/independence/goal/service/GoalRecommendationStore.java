package com.team.independence.goal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 조건 기반 목표 추천 결과를 Redis에 캐싱한다.
 * Key: goal:recommendation:{memberId} (TTL: 1일 — 근거 없음, 임의로 정함)
 *
 * 아직 목표를 생성하지 않은 단계라 goalId가 없어 memberId로 키를 잡는다.
 * 조건 입력(recommend) 시 저장하고, 결과 화면 재진입(getSaved)에서는 재계산 없이 그대로 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalRecommendationStore {

    private static final String KEY_PREFIX = "goal:recommendation:";
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<GoalRecommendationResponse> find(long memberId) {
        String json = redisTemplate.opsForValue().get(key(memberId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, GoalRecommendationResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("[목표 추천 캐시] 역직렬화 실패, 캐시 미스로 처리: memberId={}", memberId, e);
            return Optional.empty();
        }
    }

    public void save(long memberId, GoalRecommendationResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(memberId), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("[목표 추천 캐시] 직렬화 실패, 캐싱 건너뜀: memberId={}", memberId, e);
        }
    }

    private String key(long memberId) {
        return KEY_PREFIX + memberId;
    }
}
