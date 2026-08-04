package com.team.independence.goal.domain;

/** 예상 달성 시점을 계산할 때 기준으로 삼는 월 저축액의 출처. */
public enum SavingBasis {
    /** 목표에 설정된 고정 저축액 (goal.monthly_saving) */
    FIXED,
    /** 최근 3개월 실제 저축액 평균 */
    RECENT_AVERAGE,
    /** 가장 최근 달 실제 저축액 */
    LATEST
}
