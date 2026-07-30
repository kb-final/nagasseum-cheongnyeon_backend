package com.team.independence.goal.dto;

import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

/**
 * 검증·정규화된 입력을 echo하고, 순자산 + 월저축액을 연 5% 복리로 굴린 budget을 담아 반환한다.
 * marketStats/status/shortfall/adjustmentSuggestions는 다음 단계에서 추가 예정.
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
}
