package com.team.independence.goal.dto;

import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalResponse {
    private Long goalId;
    private String status;
    private String regionCode;
    private String propertyType;
    private String tradeType;
    private Integer sizeMin;
    private Integer sizeMax;
    private Long depositMin;
    private Long depositMax;
    private Long monthlyRentMin;
    private Long monthlyRentMax;
    private Long monthlySavings;
    private YearMonth targetDate;
    private Long targetAmount;
    private Long targetRentMiddleAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}