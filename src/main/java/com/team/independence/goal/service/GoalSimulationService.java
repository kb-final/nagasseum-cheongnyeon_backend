package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalForecastResponse;

public interface GoalSimulationService {

    /**
     * 월 저축액을 monthlySaving으로 바꿨다고 가정했을 때의 예상 달성 시점을 계산한다. 저장하지 않는다.
     * 0 이하면 GOAL_INVALID_INPUT, 진행 중이 아닌 목표면 GOAL_NOT_ACTIVE.
     */
    GoalForecastResponse simulateMonthlySaving(Long memberId, Long goalId, Long monthlySaving);
}
