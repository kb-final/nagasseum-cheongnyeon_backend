package com.team.independence.goal.dto;

import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

/**
 * 검증·정규화된 입력을 echo하고, 순자산 + 월저축액을 연 5% 복리로 굴린 budget과
 * 조건에 맞는 실거래 보증금 백분위수(marketStats), budget과 중앙값을 비교한
 * status/shortfall을 담아 반환한다. adjustmentSuggestions는 다음 단계에서 추가 예정.
 */
@Getter
@Builder
public class GoalDiagnosisResponse {
    private String regionCode;
    private RegionInfo region;
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

    private BudgetResult budget;
    private MarketStats marketStats;

    /** "ACHIEVABLE" | "INSUFFICIENT" | "NO_DATA" */
    private String status;
    /** INSUFFICIENT일 때만 (median - totalBudget), 그 외 null */
    private Long shortfall;
    /** INSUFFICIENT일 때만 계산, 그 외 null */
    private AdjustmentSuggestions adjustmentSuggestions;

    @Getter
    @Builder
    public static class RegionInfo {
        private String sido;
        private String sigungu;
    }

    @Getter
    @Builder
    public static class BudgetResult {
        /** recognizedAssets + projectedSavings */
        private Long totalBudget;
        /** 현재 순자산(연동자산 + manual_assets − 대출)이 목표시점까지 연 5% 복리로 불어난 값 */
        private Long recognizedAssets;
        /** 매달 monthlySavings를 목표시점까지 연 5% 복리로 적립했을 때의 미래가치 */
        private Long projectedSavings;
    }

    @Getter
    @Builder
    public static class MarketStats {
        private Long p25;
        private Long median;
        private Long p75;
        private int sampleCount;
    }

    @Getter
    @Builder
    public static class AdjustmentSuggestions {
        /** months==0이면 저축 자체가 불가능해 null */
        private IncreaseSavingsSuggestion increaseSavings;
        /** 240개월 이내에 못 찾으면 null */
        private ExtendPeriodSuggestion extendPeriod;
        /** sizeMin까지, 최대 10평 줄여도 못 찾으면 null */
        private ReduceSizeSuggestion reduceSize;
    }

    @Getter
    @Builder
    public static class IncreaseSavingsSuggestion {
        private Long additionalMonthlySavings;
        private Long adjustedMonthlySavings;
    }

    @Getter
    @Builder
    public static class ExtendPeriodSuggestion {
        private Long additionalMonths;
        private YearMonth adjustedTargetDate;
    }

    @Getter
    @Builder
    public static class ReduceSizeSuggestion {
        /** newSizeMax - 원래 sizeMax (음수) */
        private Integer deltaSizeMax;
        private Integer newSizeMax;
    }
}
