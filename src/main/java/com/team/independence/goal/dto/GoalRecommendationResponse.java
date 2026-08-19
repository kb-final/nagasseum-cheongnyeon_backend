package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class GoalRecommendationResponse {

    /** 사용자가 요청한 원래 희망 조건 — 세 추천 카드와 비교 기준으로 사용 */
    private OriginalPreference originalPreference;

    private List<RecommendationItem> recommendations;

    @Getter
    @Builder
    @Jacksonized
    public static class OriginalPreference {
        private OriginalCondition condition;
        private YearMonth targetDate;
        private long monthlySaving;
    }

    /** 사용자가 입력한 원래 조건을 그대로 담는 객체 — Condition과 구분 */
    @Getter
    @Builder
    @Jacksonized
    public static class OriginalCondition {
        private String regionCode;
        private String regionName;
        private HousingType housingType;
        private DealType dealType;
        private Integer areaMin;
        private Integer areaMax;
        private Long depositMin;
        private Long depositMax;
        private Long monthlyRentMin;
        private Long monthlyRentMax;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class RecommendationItem {
        /** 이 대안을 만든 추천 알고리즘 */
        private AlgorithmType type;
        /**
         * 추천 주거 조건. 후보를 찾지 못한 경우 null.
         * 프론트엔드는 이 필드가 null인지 확인해 카드를 비활성 스타일로 표시한다.
         */
        private Condition condition;
        /** 대출 없는 플랜 */
        private LoanXPlan loanX;
        /** 대출 있는 플랜 */
        private LoanOPlan loanO;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class Condition {
        private String regionCode;
        private String regionName;
        private HousingType housingType;
        private DealType dealType;
        private int areaMin;
        private int areaMax;
        private long depositMin;
        private long depositMax;

        /**
         * 월세 중앙값 (원). 전세({@code dealType=JEONSE})면 0.
         *
         * <p>월세는 보증금과 함께 봐야 조건이 완성되는데 플랜 쪽에는 목돈만 담기므로 여기에 둔다.
         * 알고리즘 내부에서는 월세를 전세 환산 금액으로 바꿔 비교하지만, 여기 담기는 값은
         * 환산값이 아니라 사용자가 실제로 매달 내는 금액이다.
         */
        private long monthlyRent;

        /**
         * 이 조건의 대표값을 뽑는 데 쓰인 실거래 건수.
         *
         * <p>거래가 드문 조건에서는 한두 건으로 계산된 값일 수 있어, 숫자를 얼마나 믿을지
         * 화면에서 판단할 수 있도록 함께 내려준다.
         */
        private int sampleCount;

        /** 이 추천 조건의 실거래 중앙값(원). 플랜 계산의 기준이 된 시세. */
        private long marketMedianAmount;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class LoanXPlan {
        private long targetAmount;
        private YearMonth targetDate;
        private long monthlySaving;
    }

    @Getter
    @Builder(toBuilder = true)
    @Jacksonized
    public static class LoanOPlan {
        /** DSR 기준 최대 대출 가능액 */
        private long loanAmount;
        /** median - loanAmount (자기 자금으로 모아야 할 금액) */
        private long targetAmount;
        private YearMonth targetDate;
        private long monthlySaving;
        /**
         * 대출을 받을 경우 단축 가능한 개월 수. 대출로도 달성이 불가능하면 null.
         * (대출 없는 totalMonths) − (대출 낀 totalMonths)
         */
        private Long shortenedMonths;
    }
}
