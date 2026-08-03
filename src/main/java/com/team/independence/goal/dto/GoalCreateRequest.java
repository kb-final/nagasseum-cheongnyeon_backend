package com.team.independence.goal.dto;

import java.time.YearMonth;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 목표 진단 결과를 저장한다. 진단 응답을 그대로 echo해서 받으므로,
 * targetAmount(totalBudget)와 targetRentMiddleAmount(median)는 서버가 재계산하지 않고
 * 프론트가 보내준 값을 "설정 시점" 값으로 그대로 고정한다.
 */
@Getter
@NoArgsConstructor
public class GoalCreateRequest {

    @NotBlank
    private String regionCode;

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

    /** 미래 여부는 GoalServiceImpl에서 검증(GOAL_INVALID_DATE) */
    @NotNull
    private YearMonth targetDate;

    /** 진단 응답의 budget.totalBudget */
    @NotNull
    @Positive
    private Long targetAmount;

    /** 진단 응답의 marketStats.median (설정 시점 매물 중앙값) */
    @NotNull
    @Positive
    private Long targetRentMiddleAmount;
}