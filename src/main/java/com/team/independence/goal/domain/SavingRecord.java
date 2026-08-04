package com.team.independence.goal.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 목표별 월 저축 기록. 목표당 월 1건(uk_saving_goal_ym). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingRecord {
    private Long id;
    private Long goalId;
    /** 기록 연월 YYYYMM */
    private String recordYm;
    /** 그 달 목표 저축액(그 시점 goal.monthly_saving 복사 고정) */
    private Long targetSaving;
    /** 그 달 실제 저축액(기본값은 목표와 동일, 다르게 저축한 달만 수정) */
    private Long actualSaving;
    /** 사용자가 actualSaving을 직접 수정했는지 여부 */
    private Boolean isModified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
