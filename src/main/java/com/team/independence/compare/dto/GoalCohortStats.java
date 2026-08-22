package com.team.independence.compare.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 목표 비교 코호트 집계 결과 묶음. CompareCacheStore 직렬화 대상 */
@Getter
@Setter
@NoArgsConstructor
public class GoalCohortStats {

    private int cohortSize;
    private CohortAverages averages;
    private List<DealTypeCount> dealTypeCounts;
    private List<RegionCount> regionCounts;
    private List<AchievementBucketCount> achievementBucketCounts;

    public GoalCohortStats(int cohortSize, CohortAverages averages,
            List<DealTypeCount> dealTypeCounts, List<RegionCount> regionCounts,
            List<AchievementBucketCount> achievementBucketCounts) {
        this.cohortSize = cohortSize;
        this.averages = averages;
        this.dealTypeCounts = dealTypeCounts;
        this.regionCounts = regionCounts;
        this.achievementBucketCounts = achievementBucketCounts;
    }
}
