package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PriceModelResponse {

    private String regionCode;
    private String regionName;
    private HousingType housingType;
    private DealType dealType;

    // 연간 연속 성장률(μ): 몬테카를로 drift 입력값
    private double annualDrift;

    // 표시용 연 상승률: exp(μ) - 1
    private double cagr;

    // 연율 변동성(σ): 몬테카를로 volatility 입력값
    private double annualVol;

    // 회귀에 사용된 유효 표본 월 수: 신뢰도 지표
    private int months;

    private String startYm;
    private String endYm;
}
