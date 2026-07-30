package com.team.independence.goal.dto;

import java.time.YearMonth;
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

    @NotBlank
    private String sido;

    @NotBlank
    private String sigungu;

    @NotBlank
    @Pattern(regexp = "APT|ROW_HOUSE|OFFICETEL|DETACHED")
    private String housingType;

    @NotBlank
    @Pattern(regexp = "전세|월세")
    private String dealType;

    @NotNull
    @Positive
    private Integer areaMin;

    @NotNull
    @Positive
    private Integer areaMax;

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
    private Long monthlySaving;

    /** 희망 목표 시점(년/월만 입력, 일자 없음). JSON에서는 "yyyy-MM" 형식 */
    @NotNull
    @Future
    private YearMonth targetDate;
}
