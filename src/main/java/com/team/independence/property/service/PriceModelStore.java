package com.team.independence.property.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 가격 모델(μ, σ) 추정 결과를 Redis에 캐싱한다.
 * Key: property:priceModel:{regionCode}:{housingType}:{dealType}:{areaMin}:{areaMax} (TTL: 12시간)
 *
 * PriceModel은 36개월치 실거래 원본 행을 스캔해 회귀 추정하는 고비용 연산이다.
 * 데이터는 배치로 일 1회 소량 추가되나 모델 결과는 하루 단위로 거의 변하지 않아 캐싱에 적합하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceModelStore {

    private static final String KEY_PREFIX = "property:priceModel:";
    private static final Duration TTL = Duration.ofHours(12);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<PriceModelResponse> find(PriceModelRequest request) {
        String json = redisTemplate.opsForValue().get(key(request));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PriceModelResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("[PriceModel 캐시] 역직렬화 실패, 미스로 처리: key={}", key(request), e);
            return Optional.empty();
        }
    }

    public void save(PriceModelRequest request, PriceModelResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(request), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("[PriceModel 캐시] 직렬화 실패, 캐싱 건너뜀: key={}", key(request), e);
        }
    }

    private String key(PriceModelRequest r) {
        return KEY_PREFIX + r.getRegionCode() + ":" + r.getHousingType()
                + ":" + r.getDealType() + ":" + r.getAreaMin() + ":" + r.getAreaMax();
    }
}
