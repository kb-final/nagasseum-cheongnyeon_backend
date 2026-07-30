package com.team.independence.compare.dto;

import lombok.Getter;

/**
 * 코호트 집계 조건. 모든 집계 쿼리가 이 조건으로 대상을 좁힌다.
 *
 * <p>기준 회원의 나이·순자산에서 사용자가 고른 범위만큼 벌린 구간이다.
 * 인덱스 idx_goal_snapshot_cohort (snapshot_ym, net_assets, age) 순서와 맞춰 쓴다.
 */
@Getter
public class CohortCondition {

    private final String snapshotYm;
    private final long netAssetsMin;
    private final long netAssetsMax;
    private final int ageMin;
    private final int ageMax;

    private CohortCondition(String snapshotYm, long netAssetsMin, long netAssetsMax, int ageMin, int ageMax) {
        this.snapshotYm = snapshotYm;
        this.netAssetsMin = netAssetsMin;
        this.netAssetsMax = netAssetsMax;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
    }

    /**
     * 기준 회원의 스냅샷과 비교 범위로 조건을 만든다.
     *
     * @param baseNetAssets 기준 회원의 순자산
     * @param baseAge       기준 회원의 나이
     * @param assetRange    자산 비교 범위(±원)
     * @param ageRange      나이 비교 범위(±세)
     */
    public static CohortCondition of(String snapshotYm, long baseNetAssets, int baseAge,
                                     long assetRange, int ageRange) {
        return new CohortCondition(
                snapshotYm,
                baseNetAssets - assetRange,
                baseNetAssets + assetRange,
                baseAge - ageRange,
                baseAge + ageRange);
    }
}