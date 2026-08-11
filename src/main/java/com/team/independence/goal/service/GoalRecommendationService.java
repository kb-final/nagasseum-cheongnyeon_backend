package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;

/**
 * 독립 목표 추천 진입점.
 *
 * <p>추천은 {@link RecommendationAlgorithm} 구현체들이 각자 담당하고, 이 서비스는 그 앞뒤만 맡는다.
 * <ol>
 *   <li>자산 연동 검증 (미연동 → {@code GOAL_ASSET_REQUIRED})</li>
 *   <li>주입받은 {@code List<RecommendationAlgorithm>}을 순회하며 각 알고리즘 실행</li>
 *   <li>{@link java.util.Optional#empty()}를 반환한 알고리즘은 결과에서 제외</li>
 *   <li>남은 대안이 하나도 없으면 {@code GOAL_RECOMMENDATION_NO_CANDIDATE}</li>
 * </ol>
 *
 * <p>알고리즘별 데이터 조회는 각 구현체가 알아서 하므로 이 서비스는 관여하지 않는다.
 * 알고리즘을 추가·제거할 때도 구현체만 만들고 지우면 되며 이 서비스는 수정하지 않는다.
 */
public interface GoalRecommendationService {

    /**
     * 회원의 재무 상태와 선호 조건을 바탕으로 독립 목표를 추천한다.
     *
     * @param memberId 요청 회원 ID
     * @param request  추천 요청 파라미터
     * @return 알고리즘별 추천 대안 (대안을 내지 못한 알고리즘은 제외되므로 알고리즘 수보다 적을 수 있다)
     */
    GoalRecommendationResponse recommend(long memberId, GoalRecommendationRequest request);
}
