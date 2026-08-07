package com.team.independence.compare.dto;

import lombok.Getter;
import lombok.Setter;

/** 코호트 평균값 묶음. 한 번의 쿼리로 가져온다. */
@Getter
@Setter
public class CohortAverages {

    /** 평균 순자산(원) */
    private Long averageNetAssets;

    /** 평균 목표 금액(원) */
    private Long averageTargetAmount;

    /** 평균 준비 기간(개월) */
    private Integer averagePrepMonths;

    /** 평균 달성률(%) */
    private Double cohortAverageRate;
}