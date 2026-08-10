package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 스코링 엔진이 각 전략에 넘기는 후보 단위.
 * DB 조회 결과(시세 통계)와 지역명을 함께 담는다.
 */
@Getter
@Builder
public class RecommendationCandidate {
    private String regionCode;
    private String regionName;
    private HousingType housingType;
    private DealType dealType;
    private int areaMin;
    private int areaMax;
    /** 전세 보증금 or 월세 보증금 중앙값 */
    private long medianDeposit;
    /** WOLSE만 의미 있음. JEONSE = 0 */
    private long medianMonthlyRent;
}
