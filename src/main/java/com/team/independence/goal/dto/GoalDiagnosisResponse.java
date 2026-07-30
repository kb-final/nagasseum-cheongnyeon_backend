package com.team.independence.goal.dto;

import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

/**
 * 1단계 범위: 검증·정규화된 입력을 echo. budget/매물중앙값/비교 결과는 다음 단계에서 필드 추가 예정.
 */
@Getter
@Builder
public class GoalDiagnosisResponse {
    private String regionCode;
    private String sido;
    private String sigungu;
    private String housingType;
    private String dealType;
    private Integer areaMin;
    private Integer areaMax;
    private Long depositMin;
    private Long depositMax;
    private Long monthlyRentMin;
    private Long monthlyRentMax;
    private Long monthlySaving;
    private YearMonth targetDate;
}
