package com.team.independence.compare.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GET /api/v1/goals/comparison 응답 본문. ApiResponse&lt;CompareResponse&gt;의 data에 담긴다.
 *
 * <p>코호트 인원이 최소 기준에 못 미치면 cohort를 뺀 모든 필드가 null로 나간다.
 * 그래서 숫자도 int/long이 아니라 래퍼 타입(Integer/Long/Double)을 쓴다.
 *
 * <p>Lombok 조합 이유: MyBatis가 결과를 setter로 채우므로 @Setter와 기본 생성자가 필요하고,
 * Service에서 조립할 때는 @Builder가 읽기 쉽다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompareResponse {

    /** 집계 기준월 YYYYMM */
    private String snapshotYm;

    private Cohort cohort;
    private DealTypeDistribution dealTypeDistribution;

    /** 코호트 평균 목표 금액(원) */
    private Long averageTargetAmount;

    /** 코호트 평균 준비 기간(개월) */
    private Integer averagePrepMonths;

    private AchievementDistribution achievementDistribution;
    private List<PopularRegion> popularRegions;
    private SavingRange savingRange;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Cohort {

        /** 적용된 자산 범위(±원). 순자산 기준 */
        private Long assetRange;

        /** 적용된 나이 범위(±세) */
        private Integer ageRange;

        /** 비교 대상 인원 수 */
        private Integer cohortSize;

        /** 최소 인원 미달일 때만 false로 내려간다. 충분하면 null */
        private Boolean sufficient;

        /** 최소 인원 미달일 때만 내려간다 */
        private Integer minimumRequired;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DealTypeDistribution {

        /** 가장 많은 목표 유형. 상단 문구용 */
        private String topDealType;

        private List<DealTypeItem> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DealTypeItem {

        /** JEONSE / WOLSE */
        private String dealType;

        /** 화면 표시명(전세/월세) */
        private String label;

        /** 비율(%) */
        private Double ratio;

        /** 순위. 프론트가 1위 막대를 진하게 칠하는 데 쓴다 */
        private Integer rank;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AchievementDistribution {

        /** 내 달성률(%) */
        private Double myRate;

        /** 코호트 평균 달성률(%) */
        private Double cohortAverageRate;

        private List<AchievementBucket> buckets;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AchievementBucket {

        /** 구간 하한(%) */
        private Integer rangeMin;

        /** 구간 상한(%) */
        private Integer rangeMax;

        /** 해당 구간 인원 수. 0명이어도 막대 자리를 유지하려고 내려보낸다 */
        private Integer count;

        /** 해당 구간 비율(%) */
        private Double ratio;

        /**
         * 내가 속한 구간 여부. 프론트가 이 막대에 깃발을 꽂는다.
         *
         * <p>primitive boolean으로 두면 Lombok이 isMine()을 만들고
         * Jackson이 JSON 키를 "mine"으로 뽑아버린다. 래퍼 Boolean이라야 "isMine"으로 나간다.
         */
        private Boolean isMine;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PopularRegion {

        /** 순위(1~3) */
        private Integer rank;

        /** 지역 코드 */
        private String regionCode;

        /** 지역명. region 테이블 조인 */
        private String regionName;

        /** 코호트 내 선택 비율(%) */
        private Double ratio;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SavingRange {

        /** 내 월 저축액(원) */
        private Long myMonthlySaving;

        /** 코호트 월 저축액 주요 구간 하한(원) */
        private Long cohortRangeMin;

        /** 코호트 월 저축액 주요 구간 상한(원) */
        private Long cohortRangeMax;
    }
}