package com.team.independence.compare.dto;

import com.team.independence.compare.domain.MonthlyIncomeBracket;
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
    private final Long monthlyIncomeMin;  // null이면 소득 필터 미적용
    private final Long monthlyIncomeMax;
    private final String occupationType;  // null이면 직업군 필터 미적용

    private CohortCondition(String snapshotYm,
                            long netAssetsMin, long netAssetsMax,
                            int ageMin, int ageMax,
                            Long monthlyIncomeMin, Long monthlyIncomeMax,
                            String occupationType) {
        this.snapshotYm = snapshotYm;
        this.netAssetsMin = netAssetsMin;
        this.netAssetsMax = netAssetsMax;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.monthlyIncomeMin = monthlyIncomeMin;
        this.monthlyIncomeMax = monthlyIncomeMax;
        this.occupationType = occupationType;
    }

    /** 나이·자산만으로 코호트 조건 생성. 기존 deprecated 엔드포인트용 */
    public static CohortCondition of(String snapshotYm, long baseNetAssets, int baseAge,
                                     long assetRange, int ageRange) {
        return new CohortCondition(snapshotYm,
                baseNetAssets - assetRange, baseNetAssets + assetRange,
                baseAge - ageRange, baseAge + ageRange,
                null, null, null);
    }

    /** 나이·자산·소득·직업군 코호트 조건 생성. null이면 해당 필터 미적용 */
    public static CohortCondition of(String snapshotYm, long baseNetAssets, int baseAge,
                                     long assetRange, int ageRange,
                                     MonthlyIncomeBracket incomeBracket,
                                     String occupationType) {
        return new CohortCondition(snapshotYm,
                baseNetAssets - assetRange, baseNetAssets + assetRange,
                baseAge - ageRange, baseAge + ageRange,
                incomeBracket != null ? incomeBracket.min : null,
                incomeBracket != null ? incomeBracket.max : null,
                occupationType);
    }
}