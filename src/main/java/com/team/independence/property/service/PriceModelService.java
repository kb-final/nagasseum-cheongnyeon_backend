package com.team.independence.property.service;

import com.team.independence.property.dto.PriceModelKey;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import java.util.Collection;
import java.util.Map;

public interface PriceModelService {

    /**
     * 지역 / 유형 / 면적 조건에 맞는 최근 36개월 실거래 시계열에서 연간 성장률(μ)과 연율 변동성(σ)을 추정
     */
    PriceModelResponse estimate(PriceModelRequest request);

    /**
     * 같은 지역·기간을 공유하는 여러 조합의 PriceModel을 한 번에 계산한다.
     * Redis 캐시 히트는 그대로 재사용하고, 미스 조합만 골라 단일 DB 왕복으로 raw 행을 뽑는다.
     * RealisticAlgorithm·HoldOutAlgorithm이 40조합을 준비할 때 콜드 캐시 왕복을 40 → 1로 접는 지점.
     *
     * <p>표본이 부족한 조합은 결과 맵에서 제외되므로, 호출자는 map.get(key) == null을 스킵으로 처리하면 된다.
     */
    Map<PriceModelKey, PriceModelResponse> estimateBatch(String regionCode, Collection<PriceModelKey> keys);
}
