package com.team.independence.goal.mapper;

import com.team.independence.goal.domain.GoalHousing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GoalHousingMapper {
    void insert(GoalHousing goalHousing);

    /** 목표의 주거 희망 조건을 통째로 교체한다. 모든 컬럼이 NOT NULL이라 부분 수정은 지원하지 않는다. */
    int update(GoalHousing goalHousing);

    /** 목표의 주거 희망 조건 조회(목표 1 : 1). */
    GoalHousing findByGoalId(@Param("goalId") Long goalId);
}
