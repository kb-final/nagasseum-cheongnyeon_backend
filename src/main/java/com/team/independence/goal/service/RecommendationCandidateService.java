package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.RecommendationCandidate;
import java.util.List;

/**
 * 추천 후보군 생성 — 요청 조건과 확장 조건으로 후보 목록을 수집한다.
 *
 * <ul>
 *   <li>사용자 조건 그대로 조회 (PREFERENCE용)</li>
 *   <li>housingType 제약 제거 (VALUE / REALISTIC용)</li>
 *   <li>면적 구간 확장 (HOLD_OUT용)</li>
 *   <li>sizeMin/sizeMax 미입력 시 10~30평 전 구간 자동 탐색</li>
 * </ul>
 */
public interface RecommendationCandidateService {

    /**
     * 추천 가능한 전체 후보 목록을 반환한다.
     * 후보가 전혀 없으면 빈 리스트를 반환하고, 호출자가 GOAL_RECOMMENDATION_NO_CANDIDATE를 던진다.
     */
    List<RecommendationCandidate> generateCandidates(GoalRecommendationRequest req);
}
