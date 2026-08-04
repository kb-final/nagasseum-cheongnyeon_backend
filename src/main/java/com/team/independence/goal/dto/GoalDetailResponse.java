package com.team.independence.goal.dto;

import com.team.independence.goal.domain.SavingBasis;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 목표 상세 화면 응답.
 * YearMonth는 "2027-12" 형태로 직렬화된다(RootConfig의 JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false).
 * 원시 타입 대신 래퍼 타입을 쓴다 — 값이 없는 경우를 null로 표현해야 하고,
 * boolean 필드는 Lombok getter 이름 때문에 JSON 키가 잘리는 문제가 있다.
 */
@Getter
@Builder
public class GoalDetailResponse {

    private Long goalId;
    private String goalType;
    /** ACTIVE / ACHIEVED / ARCHIVED */
    private String status;
    /** 목표 시점 */
    private YearMonth targetDate;

    private Housing housing;
    private Progress progress;
    private SavingStatus savingStatus;
    /** 저축 기준별 예상 달성 시점 */
    private List<Forecast> forecasts;

    /** 희망 주거 조건 (goal_housing) */
    @Getter
    @Builder
    public static class Housing {
        private String regionCode;
        private HousingType housingType;
        private DealType dealType;
        /** 희망 평수 범위 */
        private Integer areaMin;
        private Integer areaMax;
        /** 희망 보증금 범위 */
        private Long depositMin;
        private Long depositMax;
    }

    /** 목표 달성 현황 */
    @Getter
    @Builder
    public static class Progress {
        /** 목표 금액(설정 시점 고정) */
        private Long targetAmount;
        /** 현재 자금(순자산) */
        private Long currentAmount;
        /** 남은 금액 = 목표 − 현재, 최소 0 */
        private Long remainingAmount;
        /** 달성률(%) */
        private Double achievementRate;
    }

    /** 월 저축 현황. 저축 기록이 없으면 고정 저축액을 뺀 나머지는 null. */
    @Getter
    @Builder
    public static class SavingStatus {
        /** 고정 저축액 (goal.monthly_saving) */
        private Long fixedSaving;
        /** 최근 3개월 평균 실제 저축액. 기록 3건 미만이면 null */
        private Long recentAverageSaving;
        /** 가장 최근 달 실제 저축액. 기록이 없으면 null */
        private Long latestSaving;
    }

    /** 특정 월 저축액을 유지했을 때의 예상 달성 시점 */
    @Getter
    @Builder
    public static class Forecast {
        private SavingBasis basis;
        /** 해당 기준의 월 저축액 */
        private Long monthlySaving;
        /** 예상 달성 시점. 이미 달성했거나 탐색 상한 내에 도달하지 못하면 null */
        private YearMonth expectedDate;
        /** 고정 기준 대비 앞당겨진 개월 수(양수=단축, 0=동일, 음수=지연). 비교 불가면 null */
        private Integer monthsDiff;
    }
}
