package com.team.independence.goal.service;

import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.dto.GoalCreateRequest;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.goal.dto.GoalForecastResponse;
import com.team.independence.goal.dto.GoalResponse;

public interface GoalService {
    GoalDiagnosisResponse diagnose(Long memberId, GoalDiagnosisRequest request);

    /** 진단 결과를 목표로 저장한다. 이미 ACTIVE 목표가 있으면 GOAL_ALREADY_EXISTS로 거부한다. */
    GoalResponse createGoal(Long memberId, GoalCreateRequest request);

    /**
     * 목표를 찾고 요청자가 소유자인지 확인한다.
     * 목표가 없으면 GOAL_NOT_FOUND, 다른 회원의 목표면 GOAL_FORBIDDEN.
     */
    Goal findOwnedGoal(Long memberId, Long goalId);

    /**
     * 매달 monthlySaving씩 저축할 때 예상 예산이 targetAmount 이상이 되는 최초 개월수.
     * 진단과 동일한 복리 계산을 쓴다(예적금만 거치식 성장 + 월저축액 적립식 미래가치).
     * 이미 도달했으면 0, 저축액이 0 이하거나 탐색 상한까지 못 미치면 null.
     */
    Long calculateMonthToReach(AssetNetWorthBreakdown netWorth, long monthlySaving, long targetAmount);

    /**
     * 월 저축액을 monthlySaving으로 바꿨다고 가정했을 때의 예상 달성 시점을 계산한다. 저장하지 않는다.
     * 저축액이 0 이하면 GOAL_INVALID_INPUT, 진행 중이 아닌 목표면 GOAL_NOT_ACTIVE.
     */
    GoalForecastResponse simulateMonthlySaving(Long memberId, Long goalId, Long monthlySaving);
}
