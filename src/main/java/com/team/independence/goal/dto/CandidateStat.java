package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** RentCandidateStatMapper 조회 결과를 담는 내부 전달 객체. 5평 단위 구간화된 통계. */
@Getter
@Builder
public class CandidateStat {
    private String regionCode;
    private HousingType housingType;
    private DealType dealType;
    /** 면적 구간 하한 (평, FLOOR(area / 3.3058 / 5) * 5) */
    private int areaRangeStart;
    /** 면적 구간 상한 (평, areaRangeStart + 5) */
    private int areaRangeEnd;
    private long p25Deposit;
    private long medianDeposit;
    private long p75Deposit;
    /** WOLSE만 의미 있음. JEONSE = 0 */
    private long medianMonthlyRent;
    private BigDecimal pricePerPyeong;
    private int sampleCount;
}
