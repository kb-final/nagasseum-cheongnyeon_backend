package com.team.independence.goal.dto;

import java.time.YearMonth;
import javax.validation.Valid;
import javax.validation.constraints.Future;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoalDiagnosisRequest {

    @Valid
    @NotNull
    private RegionCondition region;

    @NotBlank
    @Pattern(regexp = "APT|ROW_HOUSE|OFFICETEL|DETACHED")
    private String propertyType;

    @NotBlank
    @Pattern(regexp = "JEONSE|WOLSE")
    private String tradeType;

    @NotNull
    @Positive
    private Integer sizeMin;

    @NotNull
    @Positive
    private Integer sizeMax;

    @NotNull
    @PositiveOrZero
    private Long depositMin;

    @NotNull
    @PositiveOrZero
    private Long depositMax;

    /** 월세 아니면 무시(서버에서 0으로 정규화) */
    private Long monthlyRentMin;

    /** 월세 아니면 무시(서버에서 0으로 정규화) */
    private Long monthlyRentMax;

    @NotNull
    @PositiveOrZero
    private Long monthlySavings;

    /** 희망 목표 시점(년/월만 입력, 일자 없음). JSON에서는 "yyyy-MM" 형식 */
    @NotNull
    @Future
    private YearMonth targetDate;

    @Getter
    @NoArgsConstructor
    public static class RegionCondition {
        @NotBlank
        private String sido;

        @NotBlank
        private String sigungu;
    }
}
