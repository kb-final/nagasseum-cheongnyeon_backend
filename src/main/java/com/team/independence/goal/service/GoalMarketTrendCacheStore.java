package com.team.independence.goal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.goal.dto.GoalMarketTrendResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 홈 화면 「매물 시세 변화」 카드 데이터를 Redis에 캐싱한다.
 * Key: goal:market-trend:{goalId}  Value: GoalMarketTrendResponse의 JSON  TTL: 35일
 *
 * <p>매월 1일 배치({@link GoalMarketTrendBatchService})가 값을 덮어써 갱신하는 게 기본 흐름이고,
 * TTL은 배치가 실패했을 때 데이터가 무한정 오래된 값으로 남지 않게 하는 안전장치다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalMarketTrendCacheStore {

    private static final String KEY_PREFIX = "goal:market-trend:";
    private static final Duration TTL = Duration.ofDays(35); // 35일 후 자동 삭제

    // Redis에 문자열 형식의 Key, Value를 저장/조회할 때 사용
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 목표 ID에 해당하는 시세 변화 캐시를 Redis에서 조회
    public Optional<GoalMarketTrendResponse> find(Long goalId) {
        String json = redisTemplate.opsForValue().get(key(goalId));
        if (json == null) {
            return Optional.empty();
        }

        // JSON을 GoalMarketTrendResponse 객체로 변환
        try {
            return Optional.of(objectMapper.readValue(json, GoalMarketTrendResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("[목표 시세 캐시] 역직렬화 실패, 캐시 미스로 처리: goalId={}", goalId, e);
            return Optional.empty();
        }
    }

    // 시세 변화 응답 객체를 Redis에 저장
    public void save(Long goalId, GoalMarketTrendResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(goalId), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("[목표 시세 캐시] 직렬화 실패, 캐싱 건너뜀: goalId={}", goalId, e);
        }
    }

    /**
     * 캐시된 시세 변화를 지운다. 캐시 값에 목표 금액·최초 중앙값·주거 조건이 모두 들어 있어
     * 목표가 수정되면 그대로 stale해지므로, 다음 조회 때 새 조건으로 다시 계산되도록 무효화한다.
     */
    public void delete(Long goalId) {
        redisTemplate.delete(key(goalId));
    }

    private String key(Long goalId) {
        return KEY_PREFIX + goalId;
    }
}