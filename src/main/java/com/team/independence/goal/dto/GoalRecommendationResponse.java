package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalRecommendationResponse {

    private List<RecommendationItem> recommendations;

    @Getter
    @Builder
    public static class RecommendationItem {
        private String title;
        private String reason;
        private Condition condition;
        /** 대출 없는 플랜 */
        private LoanXPlan loanX;
        /** 대출 있는 플랜 */
        private LoanOPlan loanO;
    }

    @Getter
    @Builder
    public static class Condition {
        private String regionCode;
        private String regionName;
        private HousingType housingType;
        private DealType dealType;
        private int areaMin;
        private int areaMax;
    }

    @Getter
    @Builder
    public static class LoanXPlan {
        private long targetAmount;
        private YearMonth targetDate;
        private long monthlySaving;
    }

    @Getter
    @Builder
    public static class LoanOPlan {
        /** DSR 기준 최대 대출 가능액 */
        private long loanAmount;
        /** median - loanAmount (자기 자금으로 모아야 할 금액) */
        private long targetAmount;
        private YearMonth targetDate;
        private long monthlySaving;
    }
}
