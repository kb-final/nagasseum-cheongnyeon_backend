package com.team.independence.goal.mapper;

import com.team.independence.goal.domain.SavingRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SavingRecordMapper {

    /**
     * 최근 저축 기록을 연월 내림차순으로 조회한다(첫 번째가 가장 최근 달).
     * 최근 3개월 평균·가장 최근 달 저축액 산출에 쓴다.
     */
    List<SavingRecord> findRecentByGoalId(@Param("goalId") Long goalId, @Param("limit") int limit);
}
