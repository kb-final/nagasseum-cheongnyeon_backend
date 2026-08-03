package com.team.independence.compare.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.team.independence.compare.domain.GoalSnapshot;
import com.team.independence.compare.dto.AchievementBucketCount;
import com.team.independence.compare.dto.CohortAverages;
import com.team.independence.compare.dto.CohortCondition;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.dto.CompareResponse.AchievementBucket;
import com.team.independence.compare.dto.CompareResponse.AchievementDistribution;
import com.team.independence.compare.dto.CompareResponse.Cohort;
import com.team.independence.compare.dto.CompareResponse.DealTypeDistribution;
import com.team.independence.compare.dto.CompareResponse.DealTypeItem;
import com.team.independence.compare.dto.CompareResponse.PopularRegion;
import com.team.independence.compare.dto.CompareResponse.SavingRange;
import com.team.independence.compare.dto.DealTypeCount;
import com.team.independence.compare.dto.RegionCount;
import com.team.independence.compare.dto.SavingRangeResult;
import com.team.independence.compare.mapper.GoalSnapshotMapper;
import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.Agreement.AgreementType;
import com.team.independence.member.service.AgreementService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 또래 비교 집계.
 *
 * <p>응답의 모든 값이 goal_snapshot 집계 결과다.
 */
@Service
@RequiredArgsConstructor
public class CompareServiceImpl implements CompareService {

    /**
     * 통계를 보여주기 위한 최소 코호트 인원. 이 수를 못 채우면 집계를 내리지 않는다.
     *
     * <p>기능명세서 데이터정책은 "코호트 10명 미만 시 미표시"라 하고,
     * API 명세서는 minimumRequired 30을 예시로 든다. 두 문서가 어긋나 있어
     * 팀에서 아직 확정하지 않았다. 우선 기능명세서를 따라 10으로 두고,
     * 확정되면 이 상수만 고치면 되도록 한 곳에 모아둔다.
     */
    private static final int MINIMUM_COHORT_SIZE = 10;

    /** 허용 자산 범위(±원). 프론트 슬라이더와 같은 값이다 */
    private static final long MIN_ASSET_RANGE = 5_000_000L;
    private static final long MAX_ASSET_RANGE = 30_000_000L;

    /** 허용 나이 범위(±세). 프론트 슬라이더와 같은 값이다 */
    private static final int MIN_AGE_RANGE = 1;
    private static final int MAX_AGE_RANGE = 5;

    /** 달성률 구간 개수. 0~10 … 70~80 여덟 칸에 마지막 80~100 한 칸 */
    private static final int BUCKET_SIZE = 9;

    /** 화면 표시명. DB에는 코드만 저장하므로 여기서 붙인다 */
    private static final Map<String, String> DEAL_TYPE_LABELS = new LinkedHashMap<>();

    static {
        DEAL_TYPE_LABELS.put("JEONSE", "전세");
        DEAL_TYPE_LABELS.put("WOLSE", "월세");
    }

    private final GoalSnapshotMapper goalSnapshotMapper;

    /**
     * 약관 동의 확인용. member 도메인의 서비스 인터페이스만 쓴다.
     *
     * <p>남의 Mapper를 직접 부르지 않는다. 동의 저장 방식이 바뀌어도 이쪽은 안 바뀐다.
     */
    private final AgreementService agreementService;

    @Override
    public CompareResponse getComparison(Long memberId, Long assetRange, Integer ageRange) {
        validateRange(assetRange, ageRange);
        validateConsent(memberId);

        String snapshotYm = goalSnapshotMapper.findLatestSnapshotYm();
        GoalSnapshot me = (snapshotYm == null)
                ? null
                : goalSnapshotMapper.findByMemberAndYm(memberId, snapshotYm);

        if (me == null) {
            throw new BusinessException(ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND);
        }

        CohortCondition condition = CohortCondition.of(
                snapshotYm, me.getNetAssets(), me.getAge(), assetRange, ageRange);
        int cohortSize = goalSnapshotMapper.countCohort(condition);

        // 인원 미달이면 여기서 끝낸다. 집계 쿼리를 아예 돌리지 않는 게 핵심이다.
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

    /**
     * '비교 기능 데이터 제공' 약관 동의 검사.
     *
     * <p>프론트도 화면에서 막지만 API는 직접 호출할 수 있다. 개인정보를 다루는 기능이라
     * 서버에서 한 번 더 막는다. 동의 기록이 아예 없으면 동의하지 않은 것으로 본다.
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
     *
     * <p>프론트 슬라이더가 이미 범위를 막고 있지만 API는 직접 호출할 수 있다.
     * 범위를 넓게 열어두면 코호트가 사실상 전체가 되어 비교의 의미가 사라진다.
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

    /**
     * 인원 미달 응답. cohort 정보만 담고 나머지는 모두 null로 나간다.
     *
     * <p>프론트는 sufficient == false 를 보고 "비교 대상이 부족해요" 화면을 띄운다.
     * 값을 0으로 채워 내리지 않는 이유는, 0과 '집계 안 함'이 화면에서 구분돼야 하기 때문이다.
     */
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

    /**
     * 목표 유형 분포. 인원이 많은 순으로 비율과 순위를 매긴다.
     *
     * <p>인원이 적은 유형을 '기타'로 합치지 않는다. 코호트 최소 인원(k-익명성)을
     * 이미 통과한 뒤라 유형이 1명이어도 그 사람이 특정되지 않고, 오히려 합치면
     * 분포를 읽기 어렵다는 리뷰 의견을 따랐다. (PR 리뷰 2026-08-03)
     *
     * <p>정렬은 쿼리의 ORDER BY에 맡긴다. 합쳐서 끼어드는 항목이 없어졌다.
     */
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

    /**
     * 달성률 분포. 10% 단위 8구간 + 마지막 80~100% 구간, 총 9개다.
     *
     * <p>쿼리는 인원이 있는 구간만 돌려주므로 9칸을 먼저 만들어두고 채운다.
     * 기능명세서: "특정구간에 인원 0 → 해당 버킷 count: 0으로 포함 (막대 자리 유지)"
     */
    private List<AchievementBucket> buildAchievementBuckets(CohortCondition condition,
                                                            int cohortSize, double myRate) {
        int[] counts = new int[BUCKET_SIZE];
        for (AchievementBucketCount row : goalSnapshotMapper.countByAchievementBucket(condition)) {
            // 쿼리의 LEAST(..., 8)가 상한을 막고 배치가 달성률을 0~100으로 저장하지만,
            // 그 중 하나만 바뀌어도 여기서 배열 밖을 짚어 500이 난다. 값을 그대로 믿지 않는다.
            int index = row.getBucketIndex();
            if (index >= 0 && index < BUCKET_SIZE) {
                counts[index] = row.getCount();
            }
        }

        int myIndex = bucketIndexOf(myRate);

        List<AchievementBucket> buckets = new ArrayList<>();
        for (int i = 0; i < BUCKET_SIZE; i++) {
            buckets.add(AchievementBucket.builder()
                    .rangeMin(i * 10)
                    .rangeMax(i == BUCKET_SIZE - 1 ? 100 : (i + 1) * 10)
                    .count(counts[i])
                    .ratio(percentage(counts[i], cohortSize))
                    .isMine(i == myIndex)
                    .build());
        }
        return buckets;
    }

    /** 달성률이 몇 번째 구간에 속하는지. 80% 이상은 모두 마지막 구간이다 (쿼리의 LEAST와 같은 규칙) */
    private int bucketIndexOf(double rate) {
        return Math.min(BUCKET_SIZE - 1, (int) (rate / 10));
    }

    /** 인기 목표 지역 TOP 3. 지역명은 쿼리에서 region 테이블을 조인해 가져온다. */
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

    /** 소수 첫째 자리까지의 백분율 */
    private double percentage(int count, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(count * 1000.0 / total) / 10.0;
    }
}