package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.ScoringContext;

/**
 * 등록된 모든 {@link RecommendationStrategy}를 실행해 추천 결과를 조립한다.
 *
 * <p>구현체는 {@code List<RecommendationStrategy>}를 주입받아 각 전략을 순차 실행하고,
 * {@link java.util.Optional#empty()}를 반환한 전략은 결과에서 제외한다.
 * 테마 추가·삭제는 전략 클래스만 추가·제거하면 되며, 이 엔진은 수정하지 않는다.
 *
 * <p>가중치 행렬 (순서: 예산여유도 / 저축부담도 / 평수효율 / 신뢰도 / 달성기간 / 선호도):
 * <pre>
 * PREFERENCE  0.10  0.10  0.10  0.15  0.10  0.45
 * REALISTIC   0.30  0.30  0.10  0.10  0.15  0.05
 * VALUE       0.10  0.15  0.40  0.25  0.05  0.05
 * HOLD_OUT    0.25  0.05  0.20  0.10  0.05  0.15  (targetDate+24개월 기준)
 * </pre>
 */
public interface RecommendationScoringEngine {

    /**
     * 컨텍스트를 각 전략에 넘겨 실행하고, 전략별 최고 점수 결과를 모아 응답을 구성한다.
     *
     * @param context 스코링에 필요한 자산·소득·후보 목록을 담은 컨텍스트
     * @return 전략별 추천 결과 (적합한 후보가 없는 전략은 결과에서 제외)
     */
    GoalRecommendationResponse score(ScoringContext context);
}
