package com.team.independence.goal.service;

import com.team.independence.goal.dto.LoanPlans;
import java.time.YearMonth;

/**
 * 모든 추천 알고리즘이 공유하는 계산기
 *
 * <p>계산 가정
 * <ul>
 *   <li>신규 대출: DSR 40% 한도, 연 3.5% 30년 원리금균등상환</li>
 *   <li>기존 대출 월 상환액: 잔액과 {@code loan_account.end_date} 잔여 기간으로 같은 금리(연 3.5%)로 역산.
 *       {@code end_date}가 null이거나 이미 만기된 대출은 상환 부담 없음으로 처리</li>
 *   <li>저축: 연 5% 복리({@link BudgetCalculator}에 위임)</li>
 *   <li>소득은 {@code member.monthly_income}, 보유 자산은 순자산 분해 결과를 사용한다</li>
 * </ul>
 *
 */
public interface LoanPlanCalculator {

    /**
     * 목표 금액과 목표 시점으로 대출 없는 플랜과 대출 낀 플랜을 함께 계산한다.
     *
     * <p>두 플랜 모두 {@code targetDate}는 동일하고, 대출 낀 플랜은 DSR 한도만큼을 목표 금액에서
     * 차감해 자력으로 모아야 할 금액을 줄인다. 결과적으로 같은 시점에 도달하는 데 필요한 월 저축액이
     * 줄어든다. DSR 한도가 0이면(소득 미등록·기존 대출 과다) loanO는 null이 된다.
     *
     * <p>대출 한도가 목표 금액을 초과하는 경우 대출액은 목표 금액으로 캡되며,
     * 자력 부담({@code LoanOPlan.targetAmount})은 0, 월 저축액도 0이 된다.
     *
     * @param memberId       요청 회원 ID (소득·자산 조회용)
     * @param requiredAmount 목표 주거를 얻는 데 필요한 금액 (원). 보통 실거래 보증금 Q3
     * @param targetDate     목표 시점
     * @return 플랜 한 쌍
     */
    LoanPlans calculate(long memberId, long requiredAmount, YearMonth targetDate);

    /**
     * DSR 40% 기준 신규 대출 최대 가능액을 계산한다.
     *
     * <p>{@link #calculate}가 내부적으로 쓰는 값이지만, 대안을 고르기 전에 예산 상한을 알아야 하는
     * 알고리즘(예: 소득 대비 현실적인 매물만 추리는 경우)이 따로 호출할 수 있도록 열어 둔다.
     *
     * <p>산출 공식: (월소득 × 0.4 − 기존 대출 월 원리금 합계)를 30년 연금 현가로 환산.
     * 소득 미등록이거나 기존 대출이 DSR 40%를 이미 소진한 경우 0을 반환한다.
     *
     * @param memberId 요청 회원 ID
     * @return 신규 대출 가능 최대 금액 (원). 한도가 없으면 0
     */
    long calcMaxLoanAmount(long memberId);
}
