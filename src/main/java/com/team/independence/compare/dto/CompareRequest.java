package com.team.independence.compare.dto;

import com.team.independence.compare.domain.CohortType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * GET /api/v1/comparison/assets · /goals 공통 요청 파라미터.
 * {@code @ModelAttribute}로 바인딩한다.
 */
@Getter
@Setter
public class CompareRequest {

    /** 자산 범위(±원). 기본값 1,000만 */
    private Long assetRange = 10_000_000L;

    /** 나이 범위(±세). 기본값 2 */
    private Integer ageRange = 2;

    /** 추가 코호트 필터. 없으면 나이+자산 기준만 적용 */
    private List<CohortType> cohortTypes;
}
