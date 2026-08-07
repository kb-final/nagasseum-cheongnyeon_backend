package com.team.independence.compare.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /api/v1/comparison/assets 응답.
 * 목표 없이 자산 연동만 있어도 조회 가능한 자산·저축 비교 데이터.
 *
 * cohort.sufficient == false이면 saving, incomeBracketDistribution,
 * occupationDistribution 모두 null이다.
 */
@Getter
@Builder
public class AssetCompareResponse {

    private String snapshotYm;
    private CompareCohort cohort;
    /** 내 월 소득(원). 미입력 시 null */
    private Long myMonthlyIncome;
    /** 코호트 평균 순자산(원) */
    private Long cohortAverageNetAssets;
    private Saving saving;
    private List<IncomeBracketItem> incomeBracketDistribution;
    private List<OccupationItem> occupationDistribution;

    @Getter
    @Builder
    public static class Saving {
        /** 내 월 저축액. 목표가 없으면 null */
        private Long mine;
        private Long cohortMin;
        private Long cohortMax;
    }

    @Getter
    @Builder
    public static class IncomeBracketItem {
        private String bracket;
        private Double ratio;
    }

    @Getter
    @Builder
    public static class OccupationItem {
        private String occupationType;
        private Double ratio;
    }
}
