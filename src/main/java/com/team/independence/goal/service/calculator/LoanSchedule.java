package com.team.independence.goal.service.calculator;

/**
 * 대출 한 건의 잔여 상환 스케줄.
 *
 * <p>단일 합산값(calcTotalExistingMonthlyPayment)으로는 각 대출의 종료 시점이 소실된다.
 * 구간별 예산 계산에서 "N개월 후 이 대출이 끝나 저축 여력이 늘어난다"는 사실을 반영하기 위해 사용한다.
 *
 * @param monthlyPayment   월 원리금 상환액 (원)
 * @param remainingMonths  오늘 기준 잔여 상환 개월 수
 */
public record LoanSchedule(long monthlyPayment, long remainingMonths) {}
