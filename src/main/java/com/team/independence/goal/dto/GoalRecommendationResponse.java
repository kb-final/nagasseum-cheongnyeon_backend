package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalRecommendationResponse {

    /** 최대 4개 (PREFERENCE / REALISTIC / VALUE / HOLD_OUT) */
    private List<RecommendationItem> recommendations;

    @Getter
    @Builder
    public static class RecommendationItem {
        private RecommendationType type;
        /** 예: "니 맘대로 해 형" */
        private String title;
        /** 추천 이유 템플릿 문자열 */
        private String reason;
        private HousingCandidate housing;
        /** 슬라이더용 — targetDate −12 ~ +36개월, 6개월 단위, 최대 9개 */
        private List<PeriodPlan> plans;
        private int score;
    }

    @Getter
    @Builder
    public static class HousingCandidate {
        private String regionCode;
        private String regionName;
        private HousingType housingType;
        private DealType dealType;
        private int areaRangeStart;
        private int areaRangeEnd;
        private long p25;
        private long median;
        private long p75;
        private BigDecimal pricePerPyeong;
        private int sampleCount;
    }

    @Getter
    @Builder
    public static class PeriodPlan {
        private YearMonth targetDate;
        /** 해당 시점의 예상 자산 */
        private long expectedAsset;
        private WithoutLoanPlan withoutLoan;
        private WithLoanPlan withLoan;
    }

    @Getter
    @Builder
    public static class WithoutLoanPlan {
        private long requiredMonthlySaving;
        /** expectedAsset >= median */
        private boolean achievable;
    }

    @Getter
    @Builder
    public static class WithLoanPlan {
        private long loanAmount;
        private long monthlyRepayment;
        /** median - loanAmount */
        private long requiredEquity;
        private long requiredMonthlySaving;
        private boolean achievable;
    }
}
