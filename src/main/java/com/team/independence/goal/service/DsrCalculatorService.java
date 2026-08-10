package com.team.independence.goal.service;

/**
 * DSR(총부채원리금상환비율) 기반 대출 한도 및 월 상환액 계산.
 * 가정: 연 3.5%, 30년 원리금균등상환.
 */
public interface DsrCalculatorService {

    /**
     * DSR 40% 기준으로 최대 대출 가능액을 역산한다.
     *
     * @param monthlyIncome          세전 월 소득 (원)
     * @param existingMonthlyRepayment 기존 대출의 월 상환액 (원)
     * @return 신규 대출 가능 최대 금액 (원). DSR 한도 초과 시 0.
     */
    long calcMaxLoanAmount(long monthlyIncome, long existingMonthlyRepayment);

    /**
     * 잔여 대출 잔액을 30년 원리금균등상환 조건으로 환산한 월 상환액을 추정한다.
     *
     * @param loanBalance 현재 대출 잔액 합계 (원)
     * @return 추정 월 상환액 (원)
     */
    long calcMonthlyRepayment(long loanBalance);
}
