package com.team.independence.goal.service.algorithm;

import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.service.MonteCarloEngine;
import com.team.independence.goal.service.MonteCarloService;
import com.team.independence.goal.service.RecommendationAlgorithm;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.service.RentMedianService;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Realistic 결과를 기준점으로 삼아 변수 하나씩 업그레이드한 조건을 추천하는 HoldOut 알고리즘.
 *
 * <h3>컨셉</h3>
 * Realistic이 "지금 저축으로 목표 시점 안에 가능한 조건"을 찾아주면,
 * HoldOut은 그 결과를 기준점(baseline)으로 삼아 변수 하나만 올린 업그레이드 후보를 탐색한다.
 * "조금 더 기다리면 한 단계 나은 집을 얻을 수 있다"는 메시지를 만드는 카드다.
 *
 * <h3>업그레이드 후보 (한 번에 하나씩)</h3>
 * <ul>
 *   <li><b>평수</b>: baseline의 areaMax보다 areaMin이 큰 바로 다음 SIZE_BUCKET</li>
 *   <li><b>주거유형</b>: 현재 타입 바로 상위 (DETACHED → ROW_HOUSE, ROW_HOUSE·OFFICETEL → APT)</li>
 *   <li><b>거래유형</b>: baseline이 월세인 경우에만 전세로 변경</li>
 * </ul>
 *
 * <h3>스코어링</h3>
 * score = 0.5 × condImprovScore + 0.5 × horizonScore
 * <ul>
 *   <li>condImprovScore: 조건 개선 폭 (평수 면적비, 주거유형·거래유형 업그레이드 고정점수)</li>
 *   <li>horizonScore = 1 / (1 + extraMonths / 24): 추가 대기 개월이 짧을수록 높다</li>
 * </ul>
 * extraMonths가 {@link #MAX_EXTRA_MONTHS}를 초과하는 후보는 제외한다.
 *
 * <h3>실행 순서 의존성</h3>
 * 이 알고리즘은 Realistic 결과에 의존한다.
 * {@link com.team.independence.goal.service.GoalRecommendationServiceImpl}에서
 * Phase 1(Realistic) 완료 후 Phase 2로 실행된다.
 * 인터페이스 메서드 {@link #recommend(long, GoalRecommendationRequest, MemberFinancialContext)}는
 * 직접 호출 시 soft-fail 카드를 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldOutAlgorithm implements RecommendationAlgorithm {

    private static final int    MIN_SAMPLE_COUNT   = 3;
    private static final long   MAX_EXTRA_MONTHS   = 36L;
    private static final double ANNUAL_INTEREST_RATE = 0.05;
    private static final long   DEPOSIT_MAX_DISPLAY  = 1_000_000_000_000L;

    private static final int MONTHS = 6;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final LoanPlanCalculator loanPlanCalculator;
    private final BudgetCalculator   budgetCalculator;
    private final RentMedianService  rentMedianService;
    private final MonteCarloService  monteCarloService;

    /** Realistic 결과 없이 직접 호출되면 soft-fail을 반환한다. */
    @Override
    public List<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request, MemberFinancialContext ctx) {
        return List.of(emptyCard());
    }

    /**
     * Realistic 결과를 받아 한 단계 업그레이드된 조건을 추천한다.
     *
     * @param realisticItem Phase 1에서 완료된 Realistic 카드. null이거나 condition이 null이면 soft-fail.
     */
    public List<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request, MemberFinancialContext ctx,
            GoalRecommendationResponse.RecommendationItem realisticItem) {

        if (realisticItem == null || realisticItem.getCondition() == null) {
            return List.of(emptyCard());
        }

        GoalRecommendationResponse.Condition baseline = realisticItem.getCondition();

        long baselineComparable = toComparableAmount(
                baseline.getMarketMedianAmount(), baseline.getMonthlyRent());
        Long baselineReachMonths = budgetCalculator.monthsToReach(
                ctx.netWorth(), ctx.rawMonthlySaving(), ctx.loanSchedules(), baselineComparable);
        if (baselineReachMonths == null) baselineReachMonths = 0L;

        long desiredMonths = request.getTargetDate() != null
                ? RecommendationAlgorithm.monthsUntil(request.getTargetDate())
                : 24L;

        YearMonth now = YearMonth.now();
        String endYm   = now.format(YM);
        String startYm = now.minusMonths(MONTHS - 1).format(YM);
        Map<String, RentMedianResponse> bulkMedians =
                rentMedianService.getBulkMedian(baseline.getRegionCode(), startYm, endYm);

        List<UpgradeCandidate> candidates = buildUpgradeCandidates(baseline);

        ScoredCandidate best = null;
        for (UpgradeCandidate uc : candidates) {
            ScoredCandidate scored = evaluate(uc, baseline, baselineReachMonths, bulkMedians, ctx, desiredMonths);
            if (scored == null) continue;
            if (best == null || scored.score() > best.score()) best = scored;
        }

        if (best == null) {
            return List.of(emptyCard());
        }

        LoanPlans plans = loanPlanCalculator.calculateSavingFixed(
                memberId, best.deposit(), ctx.netWorth(), ctx.rawMonthlySaving(), ctx.loanSchedules());

        return List.of(GoalRecommendationResponse.RecommendationItem.builder()
                .type(AlgorithmType.HOLD_OUT)
                .condition(GoalRecommendationResponse.Condition.builder()
                        .regionCode(best.regionCode())
                        .regionName(best.regionName())
                        .housingType(best.housingType())
                        .dealType(best.dealType())
                        .areaMin(best.areaMin())
                        .areaMax(best.areaMax())
                        .depositMin(0L)
                        .depositMax(DEPOSIT_MAX_DISPLAY)
                        .monthlyRent(best.monthlyRent())
                        .sampleCount(best.sampleCount())
                        .marketMedianAmount(best.deposit())
                        .build())
                .loanX(plans.getLoanX())
                .loanO(plans.getLoanO())
                .build());
    }

    // ─── 업그레이드 후보 생성 ─────────────────────────────────────────────────────

    private List<UpgradeCandidate> buildUpgradeCandidates(GoalRecommendationResponse.Condition baseline) {
        List<UpgradeCandidate> candidates = new ArrayList<>();

        // 평수 업그레이드: baseline.areaMax보다 areaMin이 큰 바로 다음 버킷
        int[] nextSize = nextSizeBucket(baseline.getAreaMax());
        if (nextSize != null) {
            candidates.add(new UpgradeCandidate(
                    baseline.getRegionCode(), baseline.getHousingType(), baseline.getDealType(),
                    nextSize[0], nextSize[1], UpgradeType.SIZE));
        }

        // 주거유형 업그레이드: 현재 타입의 바로 상위
        HousingType nextType = nextHousingType(baseline.getHousingType());
        if (nextType != null) {
            candidates.add(new UpgradeCandidate(
                    baseline.getRegionCode(), nextType, baseline.getDealType(),
                    baseline.getAreaMin(), baseline.getAreaMax(), UpgradeType.HOUSING_TYPE));
        }

        // 거래유형 업그레이드: 월세 → 전세
        if (baseline.getDealType() == DealType.WOLSE) {
            candidates.add(new UpgradeCandidate(
                    baseline.getRegionCode(), baseline.getHousingType(), DealType.JEONSE,
                    baseline.getAreaMin(), baseline.getAreaMax(), UpgradeType.DEAL_TYPE));
        }

        return candidates;
    }

    // ─── 후보 평가 ────────────────────────────────────────────────────────────────

    private ScoredCandidate evaluate(UpgradeCandidate uc, GoalRecommendationResponse.Condition baseline,
                                      long baselineReachMonths, Map<String, RentMedianResponse> bulkMedians,
                                      MemberFinancialContext ctx, long desiredMonths) {
        String key = uc.housingType() + "|" + uc.dealType() + "|" + uc.areaMin();
        RentMedianResponse median = bulkMedians.get(key);
        if (median == null || median.getSampleCount() < MIN_SAMPLE_COUNT) return null;
        if (median.getDeposit() == null || median.getDeposit().getMedian() == null) return null;

        long deposit = median.getDeposit().getMedian();
        long monthlyRent = 0L;
        if (uc.dealType() == DealType.WOLSE) {
            Long rentMedian = median.getMonthlyRent() != null ? median.getMonthlyRent().getMedian() : null;
            if (rentMedian == null) return null;
            monthlyRent = rentMedian;
        }

        // MC로 목표 시점의 예상 가격 투영 — Realistic과 동일한 1회 시뮬레이션
        long projectedDeposit = deposit;
        try {
            long budgetAtT = budgetCalculator.calculate(
                    ctx.netWorth(), ctx.rawMonthlySaving(), ctx.loanSchedules(), desiredMonths);
            MonteCarloEngine.Result mc = monteCarloService.simulate(
                    RecommendationAlgorithm.buildPriceModelRequest(
                            uc.regionCode(), uc.housingType(), uc.dealType(), uc.areaMin(), uc.areaMax()),
                    deposit, budgetAtT, (int) desiredMonths);
            projectedDeposit = mc.priceP50();
        } catch (RuntimeException e) {
            log.debug("MC 실패, 현재 시세 사용. housingType={}, dealType={}", uc.housingType(), uc.dealType());
        }

        long comparableAmount = toComparableAmount(projectedDeposit, monthlyRent);
        Long upgradeReachMonths = budgetCalculator.monthsToReach(
                ctx.netWorth(), ctx.rawMonthlySaving(), ctx.loanSchedules(), comparableAmount);
        if (upgradeReachMonths == null) return null;

        long extraMonths = upgradeReachMonths - baselineReachMonths;
        if (extraMonths > MAX_EXTRA_MONTHS) return null;

        double condImprovScore = conditionImprovementScore(baseline, uc);
        double horizonScore    = 1.0 / (1.0 + Math.max(0, extraMonths) / 24.0);
        double score           = 0.5 * condImprovScore + 0.5 * horizonScore;

        return new ScoredCandidate(
                uc.regionCode(), median.getRegionName(), uc.housingType(), uc.dealType(),
                uc.areaMin(), uc.areaMax(), projectedDeposit, monthlyRent,
                median.getSampleCount(), extraMonths, score);
    }

    private double conditionImprovementScore(GoalRecommendationResponse.Condition baseline, UpgradeCandidate uc) {
        return switch (uc.upgradeType()) {
            case SIZE -> {
                double baseMid    = (baseline.getAreaMin() + baseline.getAreaMax()) / 2.0;
                double upgradeMid = (uc.areaMin() + uc.areaMax()) / 2.0;
                yield Math.min(1.0, Math.max(0.0, 0.5 + (upgradeMid - baseMid) / 20.0));
            }
            case HOUSING_TYPE -> 0.7;
            case DEAL_TYPE    -> 0.6;
        };
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────────

    /**
     * baseline.areaMax보다 areaMin이 큰 버킷 중 가장 가까운(areaMin이 가장 작은) 버킷.
     * 이미 최대 버킷이면 null.
     */
    private int[] nextSizeBucket(int baselineAreaMax) {
        int[] result = null;
        for (int[] bucket : SIZE_BUCKETS) {
            if (bucket[0] > baselineAreaMax && (result == null || bucket[0] < result[0])) {
                result = bucket;
            }
        }
        return result;
    }

    /** DETACHED(1) → ROW_HOUSE(2), ROW_HOUSE·OFFICETEL(2) → APT(3), APT → null */
    private HousingType nextHousingType(HousingType current) {
        return switch (current) {
            case DETACHED          -> HousingType.ROW_HOUSE;
            case ROW_HOUSE, OFFICETEL -> HousingType.APT;
            case APT               -> null;
        };
    }

    private long toComparableAmount(long deposit, long monthlyRent) {
        if (monthlyRent <= 0) return deposit;
        return deposit + Math.round(monthlyRent * 12 / ANNUAL_INTEREST_RATE);
    }

    private GoalRecommendationResponse.RecommendationItem emptyCard() {
        return GoalRecommendationResponse.RecommendationItem.builder()
                .type(AlgorithmType.HOLD_OUT)
                .build();
    }

    // ─── 내부 타입 ────────────────────────────────────────────────────────────────

    private enum UpgradeType { SIZE, HOUSING_TYPE, DEAL_TYPE }

    private record UpgradeCandidate(
            String regionCode, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, UpgradeType upgradeType) {}

    private record ScoredCandidate(
            String regionCode, String regionName, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, long deposit, long monthlyRent,
            int sampleCount, long extraMonths, double score) {}
}
