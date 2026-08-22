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

    /** 특정 목표의 특정 연월 저축 기록을 조회한다. 없으면 null(아직 입력하지 않은 달, 오류 아님). */
    SavingRecord findByGoalIdAndRecordYm(@Param("goalId") Long goalId, @Param("recordYm") String recordYm);

    /**
     * 이번 달 실제 저축액을 입력/수정한다(uk_saving_goal_ym 기준 upsert).
     * targetSaving은 최초 입력 시에만 고정되고, 이후 수정에서는 갱신하지 않는다(그 시점 monthly_saving 스냅샷 유지).
     */
    void upsertActualSaving(@Param("goalId") Long goalId, @Param("recordYm") String recordYm,
            @Param("targetSaving") Long targetSaving, @Param("actualSaving") Long actualSaving);
}
