package com.team.independence.goal.dto;

/**
 * 추천 알고리즘 종류. 값 하나마다 독립적인 {@code RecommendationAlgorithm} 구현체가 대응한다.
 *
 * <p>알고리즘마다 보는 데이터도 계산 방식도 완전히 다르므로, 아래 설명은 무엇을 뽑는 알고리즘인지에
 * 대한 것일 뿐 구현 방식을 규정하지 않는다.
 *
 * <p>응답의 {@code recommendations[].type}으로 내려가, 프론트가 카드마다 어떤 성격의 추천인지
 * 라벨을 붙이는 데 쓰인다.
 */
public enum AlgorithmType {
    /** 선호 우선 — 사용자가 입력한 조건에 그대로 부합하는 목표 */
    PREFERENCE,
    /** 현실 우선 — 소득·자산에 비추어 무리 없이 달성 가능한 목표 */
    REALISTIC,
    /** 가성비 우선 — 같은 예산으로 더 나은 조건을 얻을 수 있는 목표 */
    VALUE,
    /** 미래 가능성 우선 — 더 모은 뒤에 도달할 수 있는 더 좋은 목표 */
    HOLD_OUT
}
