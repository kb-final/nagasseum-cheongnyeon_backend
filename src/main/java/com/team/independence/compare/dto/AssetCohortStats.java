package com.team.independence.compare.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 자산 비교 코호트 집계 결과 묶음. CompareCacheStore 직렬화 대상 */
@Getter
@Setter
@NoArgsConstructor
public class AssetCohortStats {

    private int cohortSize;
    private CohortAverages averages;
    private SavingRangeResult savingRange;
    private List<IncomeBracketCount> incomeBracketCounts;
    private List<OccupationTypeCount> occupationTypeCounts;

    public AssetCohortStats(int cohortSize, CohortAverages averages, SavingRangeResult savingRange,
            List<IncomeBracketCount> incomeBracketCounts, List<OccupationTypeCount> occupationTypeCounts) {
        this.cohortSize = cohortSize;
        this.averages = averages;
        this.savingRange = savingRange;
        this.incomeBracketCounts = incomeBracketCounts;
        this.occupationTypeCounts = occupationTypeCounts;
    }
}
