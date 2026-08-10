package com.team.independence.goal.dto;

public enum RecommendationType {
    /** 선호도 최우선 — 입력 조건에 가장 잘 맞는 목표 */
    PREFERENCE,
    /** 현실성 최우선 — 소득·자산·달성 가능성을 고려한 현실적 목표 */
    REALISTIC,
    /** 가성비 최우선 — 평당 가격이 낮고 표본이 충분한 주거 */
    VALUE,
    /** 미래 가능성 최우선 — 더 모은 뒤 도달 가능한 더 좋은 목표 (+24~36개월) */
    HOLD_OUT
}
