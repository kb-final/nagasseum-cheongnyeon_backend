package com.team.independence.goal.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이번 달 실제 저축액 입력/수정 공통 요청. 0원도 유효한 기록으로 허용한다. */
@Getter
@NoArgsConstructor
public class GoalSavingCurrentUpdateRequest {

    @NotNull
    @PositiveOrZero
    private Long actualSaving;
}
