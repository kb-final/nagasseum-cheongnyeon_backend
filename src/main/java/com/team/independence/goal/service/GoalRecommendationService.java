package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;

/**
 * 독립 목표 추천 진입점.
 *
 * <p>처리 순서:
 * <ol>
 *   <li>자산 연동 검증 (미연동 → GOAL_ASSET_REQUIRED)</li>
 *   <li>순자산 분류 및 자산 요약 조회</li>
 *   <li>회원 소득 정보 조회 (DSR 계산용)</li>
 *   <li>후보군 생성 (빈 결과 → GOAL_RECOMMENDATION_NO_CANDIDATE)</li>
 *   <li>목표 시점 목록 생성 (targetDate −12 ~ +36개월, 6개월 단위)</li>
 *   <li>기존 대출 월 상환액 추정</li>
 *   <li>후보별 PeriodPlan 계산</li>
 *   <li>4전략 스코링 → 각 1개 선정</li>
 * </ol>
 */
public interface GoalRecommendationService {

    /**
     * 회원의 재무 상태와 선호 조건을 바탕으로 독립 목표 4가지를 추천한다.
     *
     * @param memberId 요청 회원 ID
     * @param req      추천 요청 파라미터
     * @return 전략별 추천 결과 (최대 4개)
     */
    GoalRecommendationResponse recommend(long memberId, GoalRecommendationRequest req);
}
