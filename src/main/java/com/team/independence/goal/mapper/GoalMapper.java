package com.team.independence.goal.mapper;

import com.team.independence.goal.domain.Goal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GoalMapper {
    void insert(Goal goal);

    /** 회원에게 ACTIVE 목표가 이미 있는지 확인한다(동시 ACTIVE 목표는 1개만 허용). */
    boolean existsActiveByMemberId(@Param("memberId") Long memberId);
}
