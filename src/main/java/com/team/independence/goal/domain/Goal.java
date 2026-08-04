package com.team.independence.goal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Goal {
    private Long id;
    private Long memberId;
    private String goalType;
    private Long targetAmount;
    private Long targetRentMiddleAmount;
    /** DB 컬럼은 DATE. YearMonth를 다루는 MyBatis TypeHandler가 없어 매달 1일로 저장한다. */
    private LocalDate targetDate;
    private Long monthlySaving;
    private String status;
    private LocalDateTime marketAlertDismissedAt;
    private Long marketAlertDismissedPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
