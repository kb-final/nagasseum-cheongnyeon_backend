package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;

public interface GoalService {
    GoalDiagnosisResponse diagnose(Long memberId, GoalDiagnosisRequest request);
}
