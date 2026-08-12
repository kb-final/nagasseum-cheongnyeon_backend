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
        /** 이 대안을 만든 추천 알고리즘 */
        private AlgorithmType type;
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

        /**
         * 월세 중앙값 (원). 전세({@code dealType=JEONSE})면 0.
         *
         * <p>월세는 보증금과 함께 봐야 조건이 완성되는데 플랜 쪽에는 목돈만 담기므로 여기에 둔다.
         * 알고리즘 내부에서는 월세를 전세 환산 금액으로 바꿔 비교하지만, 여기 담기는 값은
         * 환산값이 아니라 사용자가 실제로 매달 내는 금액이다.
         */
        private long monthlyRent;
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
