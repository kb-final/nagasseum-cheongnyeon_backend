package com.team.independence.goal.dto;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 각 추천 전략이 스코링에 필요한 모든 입력을 담는 컨텍스트.
 * GoalRecommendationService가 조립해서 RecommendationScoringEngine에 넘긴다.
 */
@Getter
@Builder
public class ScoringContext {
    private GoalRecommendationRequest request;
    private AssetNetWorthBreakdown netWorth;
    /** DSR 계산용 세전 월 소득 */
    private long monthlyIncome;
    /** 기존 대출의 추정 월 상환액 */
    private long existingMonthlyRepayment;
    /** CandidateService가 수집한 전체 후보 목록 */
    private List<RecommendationCandidate> candidates;
}
