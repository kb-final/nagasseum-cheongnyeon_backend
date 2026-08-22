package com.team.independence.compare.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.compare.dto.AssetCohortStats;
import com.team.independence.compare.dto.CohortCondition;
import com.team.independence.compare.dto.GoalCohortStats;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 또래 비교 코호트 집계 결과를 Redis에 캐싱한다.
 *
 * Key 구조: compare:{type}:v1:{snapshotYm}:{netAssetsMin}:{netAssetsMax}:{ageMin}:{ageMax}:{incomeMin}:{incomeMax}:{occ}
 * TTL: 24시간. goal_snapshot은 매월 1일 배치로 갱신되므로 하루면 충분하다.
 *
 * 배치 완료 시 {@link #evictAll()}로 compare:* 전체를 무효화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareCacheStore {

    private static final String ASSETS_PREFIX = "compare:assets:v1:";
    private static final String GOALS_PREFIX = "compare:goals:v1:";
    private static final String EVICT_PATTERN = "compare:*";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<AssetCohortStats> findAssetStats(CohortCondition condition) {
        String json = redisTemplate.opsForValue().get(assetKey(condition));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AssetCohortStats.class));
        } catch (JsonProcessingException e) {
            log.warn("[비교 캐시] 자산 역직렬화 실패, 캐시 미스로 처리: key={}", assetKey(condition), e);
            return Optional.empty();
        }
    }

    public void saveAssetStats(CohortCondition condition, AssetCohortStats stats) {
        try {
            redisTemplate.opsForValue().set(assetKey(condition), objectMapper.writeValueAsString(stats), TTL);
        } catch (JsonProcessingException e) {
            log.warn("[비교 캐시] 자산 직렬화 실패, 캐싱 건너뜀: key={}", assetKey(condition), e);
        }
    }

    public Optional<GoalCohortStats> findGoalStats(CohortCondition condition) {
        String json = redisTemplate.opsForValue().get(goalKey(condition));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, GoalCohortStats.class));
        } catch (JsonProcessingException e) {
            log.warn("[비교 캐시] 목표 역직렬화 실패, 캐시 미스로 처리: key={}", goalKey(condition), e);
            return Optional.empty();
        }
    }

    public void saveGoalStats(CohortCondition condition, GoalCohortStats stats) {
        try {
            redisTemplate.opsForValue().set(goalKey(condition), objectMapper.writeValueAsString(stats), TTL);
        } catch (JsonProcessingException e) {
            log.warn("[비교 캐시] 목표 직렬화 실패, 캐싱 건너뜀: key={}", goalKey(condition), e);
        }
    }

    /** goal_snapshot 배치 완료 후 호출. 스냅샷이 교체됐으므로 compare 캐시 전체를 무효화한다. */
    public void evictAll() {
        Set<String> keys = redisTemplate.keys(EVICT_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[비교 캐시] 배치 완료 후 캐시 전체 삭제. {}건", keys.size());
        }
    }

    private String assetKey(CohortCondition c) {
        return ASSETS_PREFIX + conditionSuffix(c);
    }

    private String goalKey(CohortCondition c) {
        return GOALS_PREFIX + conditionSuffix(c);
    }

    private String conditionSuffix(CohortCondition c) {
        return c.getSnapshotYm()
                + ":" + c.getNetAssetsMin()
                + ":" + c.getNetAssetsMax()
                + ":" + c.getAgeMin()
                + ":" + c.getAgeMax()
                + ":" + c.getMonthlyIncomeMin()
                + ":" + c.getMonthlyIncomeMax()
                + ":" + c.getOccupationType();
    }
}
