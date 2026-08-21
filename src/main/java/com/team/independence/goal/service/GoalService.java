package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.goal.dto.GoalMarketTrendResponse;
import com.team.independence.goal.dto.GoalForecastResponse;
import com.team.independence.goal.dto.GoalResponse;
import com.team.independence.goal.dto.GoalSaveRequest;
import com.team.independence.goal.dto.GoalSummaryResponse;

public interface GoalService {
    GoalDiagnosisResponse diagnose(Long memberId, GoalDiagnosisRequest request);

    /** 진단 결과를 목표로 저장한다. 이미 ACTIVE 목표가 있으면 GOAL_ALREADY_EXISTS로 거부한다. */
    GoalResponse createGoal(Long memberId, GoalSaveRequest request);

    /**
     * 목표 하나를 조회한다. 수정 화면의 폼을 기존 값으로 채우는 용도라 생성·수정과 같은 응답을 쓴다.
     * 목표가 없으면 GOAL_NOT_FOUND, 다른 회원의 목표면 GOAL_FORBIDDEN.
     * 상태는 보지 않는다 — 삭제(ARCHIVED)하거나 달성(ACHIEVED)한 목표도 조회된다.
     */
    GoalResponse getGoal(Long memberId, Long goalId);

    /**
     * 목표의 금액·시점·월 저축액과 주거 희망 조건을 통째로 교체한다.
     * 목표가 없으면 GOAL_NOT_FOUND, 다른 회원의 목표면 GOAL_FORBIDDEN,
     * 진행 중이 아닌 목표면 GOAL_NOT_ACTIVE.
     */
    GoalResponse updateGoal(Long memberId, Long goalId, GoalSaveRequest request);

    /**
     * 목표를 삭제한다. 실제로는 status를 ARCHIVED로 내리는 소프트 삭제라 저축 기록과 또래 비교
     * 스냅샷은 그대로 남는다. 목표가 없으면 GOAL_NOT_FOUND, 다른 회원의 목표면 GOAL_FORBIDDEN,
     * 이미 삭제했거나 달성한 목표면 GOAL_NOT_ACTIVE.
     */
    void deleteGoal(Long memberId, Long goalId);

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
     * calculateMonthToReach와 동일하되, 기존 대출 월상환액을 monthlySaving에서 먼저 차감한 뒤 계산한다.
     * 대출이 없거나 잔액이 0이면 monthlySaving 그대로 계산한다.
     * 월저축액이 대출 상환액보다 작으면 effectiveSaving=0으로 처리한다.
     */
    Long calculateEffectiveMonthToReach(long memberId, AssetNetWorthBreakdown netWorth,
                                        long monthlySaving, long targetAmount);

    /**
     * 월 저축액을 monthlySaving으로 바꿨다고 가정했을 때의 예상 달성 시점을 계산한다. 저장하지 않는다.
     * 저축액이 0 이하면 GOAL_INVALID_INPUT, 진행 중이 아닌 목표면 GOAL_NOT_ACTIVE.
     */
    GoalForecastResponse simulateMonthlySaving(Long memberId, Long goalId, Long monthlySaving);

    /**
     * 홈 화면 「매물 시세 변화」 카드 데이터. Redis 캐시를 우선 조회하고, 캐시 미스일 때만
     * {@link #refreshMarketTrend}로 즉시 계산한다(정상적으로는 매월 1일 배치가 캐시를 채워둔다).
     */
    GoalMarketTrendResponse getMarketTrend(Long memberId);

    /** 목표 시세 변화 데이터를 다시 계산해 캐시에 덮어쓴다. 배치와 캐시 미스 fallback이 공유하는 진입점. */
    GoalMarketTrendResponse refreshMarketTrend(Long goalId);

    /**
     * 회원의 활성(ACTIVE) 목표를 조회한다. 활성 목표가 없으면 null을 반환한다(예외 아님).
     */
    GoalResponse getActiveGoal(Long memberId);

    /**
     * 홈 화면 「목표 달성 요약」 카드 데이터. 목표 조건, 목표 금액/시점, 현재 진행 상황(현재 자금/잔여 금액/
     * 달성률/예상 잔여 개월)을 한 번에 조회한다. 회원의 활성 목표가 없으면 GOAL_NOT_FOUND.
     */
    GoalSummaryResponse getSummary(Long memberId);
}
