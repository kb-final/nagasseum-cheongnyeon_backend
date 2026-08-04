package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalCreateRequest;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.goal.dto.GoalResponse;

public interface GoalService {
    GoalDiagnosisResponse diagnose(Long memberId, GoalDiagnosisRequest request);

    /** 진단 결과를 목표로 저장한다. 이미 ACTIVE 목표가 있으면 GOAL_ALREADY_EXISTS로 거부한다. */
    GoalResponse createGoal(Long memberId, GoalCreateRequest request);
}
