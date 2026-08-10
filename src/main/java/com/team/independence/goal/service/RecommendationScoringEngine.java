package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.goal.dto.CandidateStat;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import java.util.List;
import java.util.Map;

/**
 * 6개 지표를 0~100점으로 정규화한 뒤 전략별 가중치를 적용해 4가지 추천을 각각 1개씩 선정한다.
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
     * 후보 목록과 각 후보의 시점별 계획을 받아 4전략을 스코링하고 최종 응답을 조립한다.
     *
     * @param candidates 후보군 전체 (RecommendationCandidateService 결과)
     * @param planMap    후보별 PeriodPlan 목록 (GoalRecommendationService에서 사전 계산)
     * @param breakdown  회원 순자산 분류 (미래 자산 계산에 사용)
     * @param req        원본 요청 (선호도 점수 계산에 사용)
     * @return 전략별 최고 점수 후보 최대 4개
     */
    GoalRecommendationResponse selectTop4(
            List<CandidateStat> candidates,
            Map<CandidateStat, List<GoalRecommendationResponse.PeriodPlan>> planMap,
            AssetNetWorthBreakdown breakdown,
            GoalRecommendationRequest req
    );
}
