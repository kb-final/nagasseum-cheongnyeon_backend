package com.team.independence.compare.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.compare.domain.CohortType;
import com.team.independence.compare.domain.GoalSnapshot;
import com.team.independence.compare.domain.MonthlyIncomeBracket;
import com.team.independence.compare.dto.AchievementBucketCount;
import com.team.independence.compare.dto.AssetCompareResponse;
import com.team.independence.compare.dto.CompareRequest;
import com.team.independence.compare.dto.CohortAverages;
import com.team.independence.compare.dto.CohortCondition;
import com.team.independence.compare.dto.CompareCohort;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.dto.CompareResponse.AchievementBucket;
import com.team.independence.compare.dto.CompareResponse.AchievementDistribution;
import com.team.independence.compare.dto.CompareResponse.Cohort;
import com.team.independence.compare.dto.CompareResponse.DealTypeDistribution;
import com.team.independence.compare.dto.CompareResponse.DealTypeItem;
import com.team.independence.compare.dto.CompareResponse.PopularRegion;
import com.team.independence.compare.dto.CompareResponse.SavingRange;
import com.team.independence.compare.dto.DealTypeCount;
import com.team.independence.compare.dto.GoalCompareResponse;
import com.team.independence.compare.dto.IncomeBracketCount;
import com.team.independence.compare.dto.OccupationTypeCount;
import com.team.independence.compare.dto.RegionCount;
import com.team.independence.compare.dto.SavingRangeResult;
import com.team.independence.compare.mapper.GoalSnapshotMapper;
import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.Agreement.AgreementType;
import com.team.independence.member.service.AgreementService;

import lombok.RequiredArgsConstructor;

/**
 * 또래 비교 집계.
 *
 * <p>비교 대상(또래)의 값은 모두 goal_snapshot 집계 결과다.
 * 기준이 되는 내 값은 항상 현재 자산·목표를 직접 조회해서 쓴다.
 */
@Service
@RequiredArgsConstructor
public class CompareServiceImpl implements CompareService {

    /** 통계를 보여주기 위한 최소 코호트 인원 */
    private static final int MINIMUM_COHORT_SIZE = 10;

    /** 허용 자산 범위(±원) */
    private static final long MIN_ASSET_RANGE = 5_000_000L;
    private static final long MAX_ASSET_RANGE = 30_000_000L;

    /** 허용 나이 범위(±세) */
    private static final int MIN_AGE_RANGE = 1;
    private static final int MAX_AGE_RANGE = 5;

    /** 달성률 구간 개수. 0~10 … 70~80 여덟 칸에 마지막 80~100 한 칸 */
    private static final int BUCKET_SIZE = 9;

    /** 집계 기준월 형식 YYYYMM */
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 화면 표시명. DB에는 코드만 저장하므로 여기서 붙인다 */
    private static final Map<String, String> DEAL_TYPE_LABELS = new LinkedHashMap<>();

    static {
        DEAL_TYPE_LABELS.put("JEONSE", "전세");
        DEAL_TYPE_LABELS.put("WOLSE", "월세");
    }

    private final GoalSnapshotMapper goalSnapshotMapper;
    private final AgreementService agreementService;

    /** 자산 비교 */
    @Override
    public AssetCompareResponse getAssetComparison(Long memberId, CompareRequest request) {
        validateRange(request.getAssetRange(), request.getAgeRange());
        validateConsent(memberId);

        String snapshotYm = resolveSnapshotYm();

        GoalSnapshot me = goalSnapshotMapper.findLiveAssetsByMember(memberId, LocalDate.now());
        if (me == null) {
            throw new BusinessException(ErrorCode.COMPARE_ASSET_REQUIRED);
        }

        List<CohortType> applied = resolveApplied(request.getCohortTypes());
        CohortCondition condition = buildCondition(snapshotYm, me, request.getAssetRange(), request.getAgeRange(), applied);
        int cohortSize = goalSnapshotMapper.countCohort(condition);
        if (cohortSize < MINIMUM_COHORT_SIZE) {
            return AssetCompareResponse.builder()
                    .snapshotYm(snapshotYm)
                    .cohort(buildCohort(request.getAssetRange(), request.getAgeRange(), cohortSize, applied, false))
                    .build();
        }

        CohortAverages averages = goalSnapshotMapper.findAverages(condition);
        SavingRangeResult savingRange = goalSnapshotMapper.findSavingRange(condition);
        List<IncomeBracketCount> incomeCounts = goalSnapshotMapper.countByIncomeBracket(condition);
        List<OccupationTypeCount> occupationCounts = goalSnapshotMapper.countByOccupationType(condition);

        return AssetCompareResponse.builder()
                .snapshotYm(snapshotYm)
                .cohort(buildCohort(request.getAssetRange(), request.getAgeRange(), cohortSize, applied, true))
                .myMonthlyIncome(me.getMonthlyIncome())
                .cohortAverageNetAssets(averages.getAverageNetAssets())
                .saving(AssetCompareResponse.Saving.builder()
                        .mine(me.getMonthlySaving())
                        .cohortMin(savingRange.getCohortRangeMin())
                        .cohortMax(savingRange.getCohortRangeMax())
                        .build())
                .incomeBracketDistribution(toIncomeBracketItems(incomeCounts, cohortSize))
                .occupationDistribution(toOccupationItems(occupationCounts, cohortSize))
                .build();
    }

    /** 목표 비교 */
    @Override
    public GoalCompareResponse getGoalComparison(Long memberId, CompareRequest request) {
        validateRange(request.getAssetRange(), request.getAgeRange());
        validateConsent(memberId);

        String snapshotYm = resolveSnapshotYm();

        GoalSnapshot me = goalSnapshotMapper.findLiveByMember(memberId, LocalDate.now());
        if (me == null) {
            throw new BusinessException(goalSnapshotMapper.existsActiveGoal(memberId)
                    ? ErrorCode.COMPARE_ASSET_REQUIRED
                    : ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND);
        }

        List<CohortType> applied = resolveApplied(request.getCohortTypes());
        CohortCondition condition = buildCondition(snapshotYm, me, request.getAssetRange(), request.getAgeRange(), applied);
        int cohortSize = goalSnapshotMapper.countCohort(condition);
        if (cohortSize < MINIMUM_COHORT_SIZE) {
            return GoalCompareResponse.builder()
                    .snapshotYm(snapshotYm)
                    .cohort(buildCohort(request.getAssetRange(), request.getAgeRange(), cohortSize, applied, false))
                    .build();
        }

        CohortAverages averages = goalSnapshotMapper.findAverages(condition);
        List<DealTypeCount> dealTypeCounts = goalSnapshotMapper.countByDealType(condition);
        List<RegionCount> regionCounts = goalSnapshotMapper.countTopRegions(condition);
        List<AchievementBucketCount> bucketCounts = goalSnapshotMapper.countByAchievementBucket(condition);

        return GoalCompareResponse.builder()
                .snapshotYm(snapshotYm)
                .cohort(buildCohort(request.getAssetRange(), request.getAgeRange(), cohortSize, applied, true))
                .myMonthlyIncome(me.getMonthlyIncome())
                .cohortAverageNetAssets(averages.getAverageNetAssets())
                .achievement(GoalCompareResponse.Achievement.builder()
                        .mine(me.getAchievementRate())
                        .cohortAverage(averages.getCohortAverageRate())
                        .buckets(toAchievementBuckets(bucketCounts, cohortSize, me.getAchievementRate()))
                        .build())
                .dealTypeDistribution(toDealTypeItems(dealTypeCounts, cohortSize))
                .averageTargetAmount(averages.getAverageTargetAmount())
                .averagePrepMonths(averages.getAveragePrepMonths())
                .popularRegions(toRegionItems(regionCounts, cohortSize))
                .build();
    }

    /**
     * '비교 기능 데이터 제공' 약관 동의 검사.
     */
    private void validateConsent(Long memberId) {
        boolean agreed = agreementService.getAgreements(memberId).stream()
                .filter(agreement -> AgreementType.COMPARE_DATA == agreement.getAgreementType())
                .findFirst()
                .map(Agreement::isAgreed)
                .orElse(false);

        if (!agreed) {
            throw new BusinessException(ErrorCode.COMPARE_CONSENT_REQUIRED);
        }
    }

    /**
     * 비교 기준 범위 검사.
     */
    private void validateRange(Long assetRange, Integer ageRange) {
        boolean assetOk = assetRange != null
                && assetRange >= MIN_ASSET_RANGE && assetRange <= MAX_ASSET_RANGE;
        boolean ageOk = ageRange != null
                && ageRange >= MIN_AGE_RANGE && ageRange <= MAX_AGE_RANGE;

        if (!assetOk || !ageOk) {
            throw new BusinessException(ErrorCode.COMPARE_INVALID_RANGE);
        }
    }

    private String resolveSnapshotYm() {
        String ym = goalSnapshotMapper.findLatestSnapshotYm();
        return ym != null ? ym : LocalDate.now().format(YM);
    }

    /** null이나 빈 리스트를 빈 리스트로 정규화 */
    private List<CohortType> resolveApplied(List<CohortType> cohortTypes) {
        return cohortTypes != null ? cohortTypes : Collections.emptyList();
    }

    private CohortCondition buildCondition(String snapshotYm, GoalSnapshot me,
                                           Long assetRange, Integer ageRange, List<CohortType> applied) {
        MonthlyIncomeBracket incomeBracket = resolveIncomeBracket(me, applied);
        String occupationType = resolveOccupationType(me, applied);
        return CohortCondition.of(snapshotYm, me.getNetAssets(), me.getAge(),
                assetRange, ageRange, incomeBracket, occupationType);
    }

    private MonthlyIncomeBracket resolveIncomeBracket(GoalSnapshot me, List<CohortType> applied) {
        if (!applied.contains(CohortType.INCOME)) return null;
        if (me.getMonthlyIncome() == null) throw new BusinessException(ErrorCode.COMPARE_INCOME_REQUIRED);
        return MonthlyIncomeBracket.of(me.getMonthlyIncome());
    }

    private String resolveOccupationType(GoalSnapshot me, List<CohortType> applied) {
        if (!applied.contains(CohortType.OCCUPATION)) return null;
        if (me.getOccupationType() == null) throw new BusinessException(ErrorCode.COMPARE_OCCUPATION_REQUIRED);
        return me.getOccupationType();
    }

    private CompareCohort buildCohort(Long assetRange, Integer ageRange, int cohortSize,
                                      List<CohortType> applied, boolean sufficient) {
        return CompareCohort.builder()
                .assetRange(assetRange)
                .ageRange(ageRange)
                .cohortSize(sufficient ? cohortSize : null)
                .appliedFilters(applied)
                .sufficient(sufficient)
                .minimumRequired(sufficient ? null : MINIMUM_COHORT_SIZE)
                .build();
    }

    private List<AssetCompareResponse.IncomeBracketItem> toIncomeBracketItems(
            List<IncomeBracketCount> counts, int cohortSize) {
        List<AssetCompareResponse.IncomeBracketItem> items = new ArrayList<>();
        int knownCount = 0;
        for (IncomeBracketCount row : counts) {
            items.add(AssetCompareResponse.IncomeBracketItem.builder()
                    .bracket(row.getIncomeBracket())
                    .ratio(percentage(row.getCount(), cohortSize))
                    .build());
            knownCount += row.getCount();
        }
        int unknownCount = cohortSize - knownCount;
        if (unknownCount > 0) {
            items.add(AssetCompareResponse.IncomeBracketItem.builder()
                    .bracket("UNKNOWN")
                    .ratio(percentage(unknownCount, cohortSize))
                    .build());
        }
        return items;
    }

    private List<AssetCompareResponse.OccupationItem> toOccupationItems(
            List<OccupationTypeCount> counts, int cohortSize) {
        List<AssetCompareResponse.OccupationItem> items = new ArrayList<>();
        int knownCount = 0;
        for (OccupationTypeCount row : counts) {
            items.add(AssetCompareResponse.OccupationItem.builder()
                    .occupationType(row.getOccupationType())
                    .ratio(percentage(row.getCount(), cohortSize))
                    .build());
            knownCount += row.getCount();
        }
        int unknownCount = cohortSize - knownCount;
        if (unknownCount > 0) {
            items.add(AssetCompareResponse.OccupationItem.builder()
                    .occupationType("UNKNOWN")
                    .ratio(percentage(unknownCount, cohortSize))
                    .build());
        }
        return items;
    }

    private List<GoalCompareResponse.DealTypeItem> toDealTypeItems(
            List<DealTypeCount> counts, int cohortSize) {
        List<GoalCompareResponse.DealTypeItem> items = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            DealTypeCount row = counts.get(i);
            items.add(GoalCompareResponse.DealTypeItem.builder()
                    .dealType(row.getDealType())
                    .label(DEAL_TYPE_LABELS.getOrDefault(row.getDealType(), row.getDealType()))
                    .ratio(percentage(row.getCount(), cohortSize))
                    .rank(i + 1)
                    .build());
        }
        return items;
    }

    private List<GoalCompareResponse.RegionItem> toRegionItems(
            List<RegionCount> counts, int cohortSize) {
        List<GoalCompareResponse.RegionItem> items = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            RegionCount row = counts.get(i);
            items.add(GoalCompareResponse.RegionItem.builder()
                    .rank(i + 1)
                    .regionCode(row.getRegionCode())
                    .regionName(row.getRegionName())
                    .ratio(percentage(row.getCount(), cohortSize))
                    .build());
        }
        return items;
    }

    private List<GoalCompareResponse.Bucket> toAchievementBuckets(
            List<AchievementBucketCount> counts, int cohortSize, Double myRate) {
        int[] bucketCounts = new int[BUCKET_SIZE];
        for (AchievementBucketCount row : counts) {
            int index = row.getBucketIndex();
            if (index >= 0 && index < BUCKET_SIZE) {
                bucketCounts[index] = row.getCount();
            }
        }

        int myIndex = myRate != null ? bucketIndexOf(myRate) : -1;
        List<GoalCompareResponse.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < BUCKET_SIZE; i++) {
            buckets.add(GoalCompareResponse.Bucket.builder()
                    .rangeMin(i * 10)
                    .rangeMax(i == BUCKET_SIZE - 1 ? 100 : (i + 1) * 10)
                    .count(bucketCounts[i])
                    .ratio(percentage(bucketCounts[i], cohortSize))
                    .isMine(myIndex >= 0 && i == myIndex)
                    .build());
        }
        return buckets;
    }

    /** 달성률이 몇 번째 구간에 속하는지 검사 */
    private int bucketIndexOf(double rate) {
        return Math.min(BUCKET_SIZE - 1, (int) (rate / 10));
    }

    /** 소수 첫째 자리까지의 백분율 */
    private double percentage(int count, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(count * 1000.0 / total) / 10.0;
    }

    // =========================================================
    // Deprecated — /api/v1/comparison 제거 시 아래 블록 전체 삭제
    // =========================================================

    @Override
    @Deprecated
    public CompareResponse getComparison(Long memberId, Long assetRange, Integer ageRange) {
        validateRange(assetRange, ageRange);
        validateConsent(memberId);

        String snapshotYm = goalSnapshotMapper.findLatestSnapshotYm();
        if (snapshotYm == null) {
            snapshotYm = LocalDate.now().format(YM);
        }

        GoalSnapshot me = findBaseline(memberId, snapshotYm);

        if (me == null) {
            throw new BusinessException(goalSnapshotMapper.existsActiveGoal(memberId)
                    ? ErrorCode.COMPARE_ASSET_REQUIRED
                    : ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND);
        }

        CohortCondition condition = CohortCondition.of(
                snapshotYm, me.getNetAssets(), me.getAge(), assetRange, ageRange);
        int cohortSize = goalSnapshotMapper.countCohort(condition);

        if (cohortSize < MINIMUM_COHORT_SIZE) {
            return insufficient(snapshotYm, assetRange, ageRange, cohortSize);
        }

        CohortAverages averages = goalSnapshotMapper.findAverages(condition);
        SavingRangeResult savingRange = goalSnapshotMapper.findSavingRange(condition);

        return CompareResponse.builder()
                .snapshotYm(snapshotYm)
                .cohort(Cohort.builder()
                        .assetRange(assetRange)
                        .ageRange(ageRange)
                        .cohortSize(cohortSize)
                        .build())
                .dealTypeDistribution(buildDealTypeDistribution(condition, cohortSize))
                .averageTargetAmount(averages.getAverageTargetAmount())
                .averagePrepMonths(averages.getAveragePrepMonths())
                .popularRegions(buildPopularRegions(condition, cohortSize))
                .achievementDistribution(AchievementDistribution.builder()
                        .myRate(me.getAchievementRate())
                        .cohortAverageRate(averages.getCohortAverageRate())
                        .buckets(buildAchievementBuckets(condition, cohortSize, me.getAchievementRate()))
                        .build())
                .savingRange(SavingRange.builder()
                        .myMonthlySaving(me.getMonthlySaving())
                        .cohortRangeMin(savingRange.getCohortRangeMin())
                        .cohortRangeMax(savingRange.getCohortRangeMax())
                        .build())
                .build();
    }

    /** @deprecated {@link #getComparison} 전용. 스냅샷 없으면 live 값으로 폴백 */
    @Deprecated
    private GoalSnapshot findBaseline(Long memberId, String snapshotYm) {
        GoalSnapshot snapshot = goalSnapshotMapper.findByMemberAndYm(memberId, snapshotYm);
        return snapshot != null ? snapshot : goalSnapshotMapper.findLiveByMember(memberId, LocalDate.now());
    }

    /** @deprecated {@link #getComparison} 전용. 인원 미달 시 cohort만 담아 반환 */
    @Deprecated
    private CompareResponse insufficient(String snapshotYm, Long assetRange,
                                         Integer ageRange, int cohortSize) {
        return CompareResponse.builder()
                .snapshotYm(snapshotYm)
                .cohort(Cohort.builder()
                        .assetRange(assetRange)
                        .ageRange(ageRange)
                        .cohortSize(cohortSize)
                        .sufficient(false)
                        .minimumRequired(MINIMUM_COHORT_SIZE)
                        .build())
                .build();
    }

    /** @deprecated {@link #getComparison} 전용. 목표 유형 분포(CompareResponse 형식) */
    @Deprecated
    private DealTypeDistribution buildDealTypeDistribution(CohortCondition condition, int cohortSize) {
        List<DealTypeCount> counts = goalSnapshotMapper.countByDealType(condition);

        List<DealTypeItem> items = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            DealTypeCount row = counts.get(i);
            items.add(DealTypeItem.builder()
                    .dealType(row.getDealType())
                    .label(DEAL_TYPE_LABELS.getOrDefault(row.getDealType(), row.getDealType()))
                    .ratio(percentage(row.getCount(), cohortSize))
                    .rank(i + 1)
                    .build());
        }

        return DealTypeDistribution.builder()
                .topDealType(items.isEmpty() ? null : items.get(0).getDealType())
                .items(items)
                .build();
    }

    /** @deprecated {@link #getComparison} 전용. 달성률 구간 분포(CompareResponse 형식) */
    @Deprecated
    private List<AchievementBucket> buildAchievementBuckets(CohortCondition condition,
                                                            int cohortSize, Double myRate) {
        int[] counts = new int[BUCKET_SIZE];
        for (AchievementBucketCount row : goalSnapshotMapper.countByAchievementBucket(condition)) {
            int index = row.getBucketIndex();
            if (index >= 0 && index < BUCKET_SIZE) {
                counts[index] = row.getCount();
            }
        }

        int myIndex = myRate != null ? bucketIndexOf(myRate) : -1;

        List<AchievementBucket> buckets = new ArrayList<>();
        for (int i = 0; i < BUCKET_SIZE; i++) {
            buckets.add(AchievementBucket.builder()
                    .rangeMin(i * 10)
                    .rangeMax(i == BUCKET_SIZE - 1 ? 100 : (i + 1) * 10)
                    .count(counts[i])
                    .ratio(percentage(counts[i], cohortSize))
                    .isMine(myIndex >= 0 && i == myIndex)
                    .build());
        }
        return buckets;
    }

    /** @deprecated {@link #getComparison} 전용. 인기 지역 TOP 3(CompareResponse 형식) */
    @Deprecated
    private List<PopularRegion> buildPopularRegions(CohortCondition condition, int cohortSize) {
        List<RegionCount> counts = goalSnapshotMapper.countTopRegions(condition);

        List<PopularRegion> regions = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            RegionCount row = counts.get(i);
            regions.add(PopularRegion.builder()
                    .rank(i + 1)
                    .regionCode(row.getRegionCode())
                    .regionName(row.getRegionName())
                    .ratio(percentage(row.getCount(), cohortSize))
                    .build());
        }
        return regions;
    }
}
