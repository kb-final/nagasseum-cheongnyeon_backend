package com.team.independence.compare.dto;

import com.team.independence.compare.domain.CohortType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 자산·목표 비교 탭 공통 코호트 정보.
 * sufficient가 false일 때만 minimumRequired가 내려간다.
 */
@Getter
@Builder
public class CompareCohort {

    private Long assetRange;
    private Integer ageRange;
    private Integer cohortSize;

    /** 요청에서 실제로 적용된 추가 필터 목록 */
    private List<CohortType> appliedFilters;

    /** 인원 미달일 때만 false. 충분하면 null */
    private Boolean sufficient;
    private Integer minimumRequired;
}
