package com.team.independence.goal.mapper;

import com.team.independence.goal.domain.GoalHousing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GoalHousingMapper {
    void insert(GoalHousing goalHousing);

    /** 목표의 주거 희망 조건 조회(목표 1 : 1). */
    GoalHousing findByGoalId(@Param("goalId") Long goalId);
}
