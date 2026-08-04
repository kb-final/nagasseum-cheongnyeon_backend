package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalDetailResponse;

public interface GoalDetailService {

    /**
     * 목표 상세 화면 데이터를 조회한다.
     * 목표가 없으면 GOAL_NOT_FOUND, 다른 회원의 목표면 GOAL_FORBIDDEN.
     */
    GoalDetailResponse getGoalDetail(Long memberId, Long goalId);
}
