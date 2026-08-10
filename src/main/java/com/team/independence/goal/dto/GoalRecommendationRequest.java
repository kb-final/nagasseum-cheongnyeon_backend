package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoalRecommendationRequest {

    @NotNull
    private String regionCode;

    /** 선택. null이면 전 주거유형 탐색 */
    private HousingType propertyType;

    @NotNull
    private DealType tradeType;

    /** 선택. 단위: 평. null이면 10~30평 전 구간 자동 탐색 */
    private Integer sizeMin;

    /** 선택. 단위: 평. null이면 10~30평 전 구간 자동 탐색 */
    private Integer sizeMax;

    @NotNull
    private YearMonth targetDate;
}
