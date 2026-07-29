package com.team.independence.compare.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.dto.CompareResponse.AchievementBucket;
import com.team.independence.compare.dto.CompareResponse.AchievementDistribution;
import com.team.independence.compare.dto.CompareResponse.Cohort;
import com.team.independence.compare.dto.CompareResponse.DealTypeDistribution;
import com.team.independence.compare.dto.CompareResponse.DealTypeItem;
import com.team.independence.compare.dto.CompareResponse.PopularRegion;
import com.team.independence.compare.dto.CompareResponse.SavingRange;

import lombok.RequiredArgsConstructor;

/**
 * TODO: 지금은 명세서 예시값을 그대로 돌려주는 임시 구현이다.
 * 프론트와 연결 자체를 먼저 검증하려고 만들었고, goal_snapshot 집계 쿼리로 하나씩 교체한다.
 */
@Service
@RequiredArgsConstructor
public class CompareServiceImpl implements CompareService {

    // TODO: 매퍼 붙이면 주입한다
    // private final GoalSnapshotMapper goalSnapshotMapper;

    @Override
    public CompareResponse getComparison(Long memberId, Long assetRange, Integer ageRange) {
        return CompareResponse.builder()
                .snapshotYm("202607")
                .cohort(Cohort.builder()
                        .assetRange(assetRange)
                        .ageRange(ageRange)
                        .cohortSize(247)
                        .build())
                .dealTypeDistribution(DealTypeDistribution.builder()
                        .topDealType("JEONSE")
                        .items(List.of(
                                DealTypeItem.builder()
                                        .dealType("JEONSE").label("전세").ratio(73.0).rank(1).build(),
                                DealTypeItem.builder()
                                        .dealType("WOLSE").label("월세").ratio(27.0).rank(2).build()))
                        .build())
                .averageTargetAmount(24_000_000L)
                .averagePrepMonths(14)
                .achievementDistribution(AchievementDistribution.builder()
                        .myRate(60.0)
                        .cohortAverageRate(52.0)
                        .buckets(List.of(
                                bucket(0, 10, 3, 1.2, false),
                                bucket(10, 20, 12, 4.9, false),
                                bucket(20, 30, 18, 7.3, false),
                                bucket(30, 40, 34, 13.8, false),
                                bucket(40, 50, 52, 21.1, false),
                                bucket(50, 60, 61, 24.7, false),
                                bucket(60, 70, 38, 15.4, true),
                                bucket(70, 80, 19, 7.7, false),
                                bucket(80, 100, 10, 4.0, false)))
                        .build())
                .popularRegions(List.of(
                        region(1, "11680", "강남구", 38.0),
                        region(2, "11440", "마포구", 26.0),
                        region(3, "11410", "서대문구", 17.0)))
                .savingRange(SavingRange.builder()
                        .myMonthlySaving(900_000L)
                        .cohortRangeMin(700_000L)
                        .cohortRangeMax(900_000L)
                        .build())
                .build();
    }

    private AchievementBucket bucket(int min, int max, int count, double ratio, boolean isMine) {
        return AchievementBucket.builder()
                .rangeMin(min)
                .rangeMax(max)
                .count(count)
                .ratio(ratio)
                .isMine(isMine)
                .build();
    }

    private PopularRegion region(int rank, String code, String name, double ratio) {
        return PopularRegion.builder()
                .rank(rank)
                .regionCode(code)
                .regionName(name)
                .ratio(ratio)
                .build();
    }
}