package com.team.independence.property.service;

import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;

public interface PriceModelService {

    /**
     * 지역 / 유형 / 면적 조건에 맞는 최근 36개월 실거래 시계열에서 연간 성장률(μ)과 연율 변동성(σ)을 추정
     */
    PriceModelResponse estimate(PriceModelRequest request);
}
