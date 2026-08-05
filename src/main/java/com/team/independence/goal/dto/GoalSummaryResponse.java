package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

/**
 * 홈 화면 「목표 달성 요약」 카드 응답.
 * 표시용 가공(금액 단위 변환, 연월 문자열 포맷 등)은 하지 않고 raw 값만 내려준다. FE가 화면에 맞게 포맷한다.
 */
@Getter
@Builder
public class GoalSummaryResponse {

    private Long goalId;
    private String goalType;

    private Housing housing;
    private Long targetAmount;
    /** 목표 시점 */
    private YearMonth targetDate;

    private Progress progress;

    /** 희망 주거 조건 (goal_housing) */
    @Getter
    @Builder
    public static class Housing {
        private String regionName;
        private HousingType housingType;
        private DealType dealType;
        private Integer areaMin;
        private Integer areaMax;
    }

    /** 목표 달성 현황 */
    @Getter
    @Builder
    public static class Progress {
        /** 현재 자금(순자산) */
        private Long currentAmount;
        /** 남은 금액 = 목표 − 현재, 최소 0 */
        private Long remainingAmount;
        /** 달성률(%), 0~100으로 clamp */
        private Double achievementRate;
        /** 목표 시점(target_date)까지 남은 개월수. 재계산하지 않고 저장된 target_date 기준. 이미 지났으면 0 */
        private Long remainingMonths;
    }
}
