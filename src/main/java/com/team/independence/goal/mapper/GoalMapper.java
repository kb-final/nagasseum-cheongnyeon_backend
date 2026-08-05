package com.team.independence.goal.mapper;

import com.team.independence.goal.domain.Goal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GoalMapper {
    void insert(Goal goal);

    /** 회원에게 ACTIVE 목표가 이미 있는지 확인한다(동시 ACTIVE 목표는 1개만 허용). */
    boolean existsActiveByMemberId(@Param("memberId") Long memberId);

    /** 회원의 ACTIVE 목표를 조회한다. 없으면 null. */
    Goal findActiveByMemberId(@Param("memberId") Long memberId);

    /** id로 목표를 조회한다. 없으면 null. */
    Goal findById(@Param("goalId") Long goalId);

    /** ACTIVE 상태인 모든 목표의 id 목록. 목표 시세 변화 배치 대상 조회용. */
    List<Long> findAllActiveGoalIds();
}
