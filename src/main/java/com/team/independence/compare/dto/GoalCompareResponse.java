package com.team.independence.compare.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /api/v1/comparison/goals 응답.
 * 목표 + 자산 연동이 모두 있어야 조회 가능한 목표 달성 비교 데이터.
 *
 * cohort.sufficient == false이면 achievement, dealTypeDistribution,
 * popularRegions, averageTargetAmount, averagePrepMonths 모두 null이다.
 */
@Getter
@Builder
public class GoalCompareResponse {

    private String snapshotYm;
    private CompareCohort cohort;
    /** 내 월 소득(원). 미입력 시 null */
    private Long myMonthlyIncome;
    /** 코호트 평균 순자산(원) */
    private Long cohortAverageNetAssets;
    private Achievement achievement;
    private List<DealTypeItem> dealTypeDistribution;
    private Long averageTargetAmount;
    private Integer averagePrepMonths;
    private List<RegionItem> popularRegions;

    @Getter
    @Builder
    public static class Achievement {
        /** 내 달성률(%) */
        private Double mine;
        /** 코호트 평균 달성률(%) */
        private Double cohortAverage;
        private List<Bucket> buckets;
    }

    /**
     * 달성률 10% 단위 구간. 마지막 구간은 90~100%이다.
     * isMine은 primitive boolean이 아닌 Boolean — Jackson이 "isMine"으로 직렬화하려면 래퍼 타입이어야 한다.
     */
    @Getter
    @Builder
    public static class Bucket {
        private Integer rangeMin;
        private Integer rangeMax;
        private Integer count;
        private Double ratio;
        private Boolean isMine;
    }

    @Getter
    @Builder
    public static class DealTypeItem {
        /** JEONSE / WOLSE */
        private String dealType;
        /** 화면 표시명(전세/월세) */
        private String label;
        private Double ratio;
        /** 인원 많은 순. 1위부터 */
        private Integer rank;
    }

    @Getter
    @Builder
    public static class RegionItem {
        private Integer rank;
        private String regionCode;
        private String regionName;
        private Double ratio;
    }
}
