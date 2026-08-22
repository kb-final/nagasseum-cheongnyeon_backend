package com.team.independence.goal.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 홈 화면 「이번 달 저축 기록」 카드 응답.
 * 아직 입력하지 않은 달은 actualSaving=null, recorded=false, differenceAmount=null로 내려간다(오류 아님).
 */
@Getter
@Builder
public class GoalSavingCurrentResponse {

    /** 기록 연월 YYYYMM */
    private String recordYm;
    /** 그 달 목표 저축액(goal.monthly_saving) */
    private Long targetSaving;
    /** 사용자가 입력한 실제 저축액. 미입력이면 null */
    private Long actualSaving;
    /** 이번 달 실제 저축액 입력 여부 */
    private boolean recorded;
    /** actualSaving - targetSaving. 미입력이면 null */
    private Long differenceAmount;
}
