package com.team.independence.property.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.property.dto.PriceModelKey;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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

    /**
     * 같은 지역의 여러 조합을 MGET 한 번으로 조회한다. 히트한 조합만 담긴 맵을 반환한다.
     *
     * <p>조합마다 {@link #find}를 부르면 40개 키에 왕복 40회가 나가고, Realistic·HoldOut이
     * 연달아 부르므로 요청 하나에 80회가 된다. 키 순서와 결과 순서가 대응하는 MGET으로 한 번에 받는다.
     */
    public Map<PriceModelKey, PriceModelResponse> findAll(String regionCode, Collection<PriceModelKey> keys) {
        Map<PriceModelKey, PriceModelResponse> hits = new HashMap<>();
        if (keys.isEmpty()) {
            return hits;
        }

        List<PriceModelKey> ordered = new ArrayList<>(keys);
        List<String> redisKeys = new ArrayList<>(ordered.size());
        for (PriceModelKey key : ordered) {
            redisKeys.add(key(regionCode, key));
        }

        List<String> values = redisTemplate.opsForValue().multiGet(redisKeys);
        if (values == null) {
            return hits;
        }

        for (int i = 0; i < ordered.size() && i < values.size(); i++) {
            String json = values.get(i);
            if (json == null) {
                continue;
            }
            try {
                hits.put(ordered.get(i), objectMapper.readValue(json, PriceModelResponse.class));
            } catch (JsonProcessingException e) {
                log.warn("[PriceModel 캐시] 역직렬화 실패, 미스로 처리: key={}", redisKeys.get(i), e);
            }
        }
        return hits;
    }

    public void save(PriceModelRequest request, PriceModelResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(request), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("[PriceModel 캐시] 직렬화 실패, 캐싱 건너뜀: key={}", key(request), e);
        }
    }

    /**
     * 같은 지역의 여러 조합을 파이프라인 한 번으로 저장한다.
     *
     * <p>배치 미스가 40개면 개별 저장은 SET 왕복 40회다. 콜드 캐시 응답에 그대로 얹히므로 묶는다.
     */
    public void saveAll(String regionCode, Map<PriceModelKey, PriceModelResponse> models) {
        if (models.isEmpty()) {
            return;
        }

        Map<String, String> serialized = new HashMap<>();
        for (Map.Entry<PriceModelKey, PriceModelResponse> entry : models.entrySet()) {
            String redisKey = key(regionCode, entry.getKey());
            try {
                serialized.put(redisKey, objectMapper.writeValueAsString(entry.getValue()));
            } catch (JsonProcessingException e) {
                log.warn("[PriceModel 캐시] 직렬화 실패, 캐싱 건너뜀: key={}", redisKey, e);
            }
        }
        if (serialized.isEmpty()) {
            return;
        }

        // TTL을 키마다 걸어야 해서 MSET을 못 쓴다. SETEX를 파이프라인으로 묶어 왕복만 1회로 만든다.
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                ValueOperations<String, String> ops =
                        ((RedisOperations<String, String>) operations).opsForValue();
                serialized.forEach((k, v) -> ops.set(k, v, TTL));
                return null;
            }
        });
    }

    private String key(PriceModelRequest r) {
        return KEY_PREFIX + r.getRegionCode() + ":" + r.getHousingType()
                + ":" + r.getDealType() + ":" + r.getAreaMin() + ":" + r.getAreaMax();
    }

    /** 배치용 키. {@link #key(PriceModelRequest)}와 같은 형식이어야 단건·배치 캐시가 서로를 재사용한다. */
    private String key(String regionCode, PriceModelKey k) {
        return KEY_PREFIX + regionCode + ":" + k.housingType()
                + ":" + k.dealType() + ":" + k.areaMin() + ":" + k.areaMax();
    }
}
