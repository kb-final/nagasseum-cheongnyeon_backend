package com.team.independence.goal.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 하나의 추천 대안에 대한 대출 없는 플랜 / 대출 낀 플랜 한 쌍.
 *
 * <p>모든 알고리즘이 같은 계산식으로 만든 값이어야 카드끼리 비교가 되므로,
 * 알고리즘이 직접 조립하지 않고 {@code LoanPlanCalculator}가 만들어 준다.
 */
@Getter
@Builder
public class LoanPlans {

    /** 대출 없이 전액 자력으로 모으는 플랜 */
    private GoalRecommendationResponse.LoanXPlan loanX;

    /** DSR 한도만큼 대출을 끼는 플랜. 대출 한도가 0이면 null */
    private GoalRecommendationResponse.LoanOPlan loanO;
}
