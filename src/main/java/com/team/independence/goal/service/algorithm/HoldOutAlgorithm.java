package com.team.independence.goal.service.algorithm;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.mapper.GoalMapper;
import com.team.independence.goal.service.MonteCarloEngine;
import com.team.independence.goal.service.MonteCarloService;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.goal.service.RecommendationAlgorithm;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.service.RentMedianService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * "극한 저축으로 존버하면 원하는 조건을 얼마나 맞출 수 있는가"를 계산하는 HoldOut 추천 알고리즘.
 *
 * <p>지역을 기준으로 주거유형·거래유형·면적 버킷의 조합으로 후보를 생성한다.
 * 각 후보에 대해 실거래 Q3 시세와 예산 도달 개월을 구한 뒤
 * 조건 개선(cIS)·대기 패널티(WP)·여유 자산(AM) 스코어로 비교해 가장 높은 점수의 후보를 추천한다.
 *
 * <p>기준 조건 결정 우선순위:
 * <ol>
 *   <li>request에 조건이 있으면 사용 (regionCode만 필수, 나머지는 null 허용)</li>
 *   <li>없으면 활성 목표의 GoalHousing 사용</li>
 *   <li>regionCode마저 없으면 Optional.empty()</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldOutAlgorithm implements RecommendationAlgorithm {

    private static final int  MIN_SAMPLE_COUNT = 10;
    private static final long WIDE_DEPOSIT_MAX  = 1_000_000_000_000L;
    private static final int  MAX_MC_ITERATIONS = 3;

    /**
     * 추가 대기 허용 범위 (개월).
     * n=0이어도 최소 24개월(2년)까지는 HoldOut 후보로 허용하고,
     * 어떤 상황에서도 36개월(3년)을 초과하는 대기는 추천하지 않는다.
     */
    private static final long MIN_EXTRA_MONTHS = 24;
    private static final long MAX_EXTRA_MONTHS = 36;

    /** dealType/면적 조건이 없을 때의 탐색 기본값 */
    private static final long DEFAULT_RENT_MAX = 2_000_000L;

    private final LoanPlanCalculator  loanPlanCalculator;
    private final BudgetCalculator    budgetCalculator;
    private final GoalMapper          goalMapper;
    private final GoalHousingMapper   goalHousingMapper;
    private final AssetSummaryService assetSummaryService;
    private final RentMedianService   rentMedianService;
    private final MonteCarloService   monteCarloService;

    @Override
    public Optional<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request
    ) {
        Goal activeGoal = goalMapper.findActiveByMemberId(memberId);
        GoalHousing housing = (activeGoal != null)
                ? goalHousingMapper.findByGoalId(activeGoal.getId())
                : null;

        BaseCondition base = resolveCondition(request, housing, activeGoal);
        if (base == null) return Optional.empty();

        YearMonth now = YearMonth.now();
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long rawMonthlySaving = assetSummaryService.getMonthlySavingsOrZero(memberId);
        long loanPayment = loanPlanCalculator.calcTotalExistingMonthlyPayment(memberId);
        long baseSaving = Math.max(0, rawMonthlySaving - loanPayment);
        long n = base.targetDate != null ? Math.max(0, ChronoUnit.MONTHS.between(now, base.targetDate)) : 0;

        long maxExtra = Math.min(Math.max(n, MIN_EXTRA_MONTHS), MAX_EXTRA_MONTHS);

        List<FetchedCandidate> pool = buildPool(generateCandidates(base), base, netWorth, baseSaving, n + maxExtra);

        ScoredCandidate best = null;
        ScoredCandidate bestAlreadyAchievable = null;
        for (FetchedCandidate fc : pool) {
            ScoredCandidate scored = evaluate(fc, base, netWorth, baseSaving, n, maxExtra);
            if (scored == null) continue;
            if (scored.m == 0) {
                if (bestAlreadyAchievable == null || scored.score > bestAlreadyAchievable.score) bestAlreadyAchievable = scored;
            } else {
                if (best == null || scored.score > best.score) best = scored;
            }
        }

        // HoldOut 범위(1~maxExtra) 후보가 없으면 현재 계획으로 이미 달성 가능한 후보로 대체
        boolean isAlreadyAchievable = (best == null);
        if (isAlreadyAchievable) {
            best = bestAlreadyAchievable;
        }
        if (best == null) {
            return Optional.empty();
        }

        log.info("[HoldOut] 최종 선택 memberId={} {} futurePrice={} m={}개월 score={} alreadyAchievable={}",
                memberId, best.candidate.label(),
                best.futurePrice, best.m, String.format("%.4f", best.score), isAlreadyAchievable);

        YearMonth targetDate = now.plusMonths(best.totalMonths);
        LoanPlans plans = loanPlanCalculator.calculate(memberId, best.futurePrice, targetDate);

        GoalRecommendationResponse.LoanOPlan loanO = plans.getLoanO();
        if (loanO != null) {
            Long shortened = loanPlanCalculator.calcShortenedMonths(
                    memberId, netWorth, best.effectiveSaving, best.futurePrice, best.totalMonths);
            loanO = loanO.toBuilder().shortenedMonths(shortened).build();
        }

        String title = isAlreadyAchievable
                ? "현재 계획대로면 원하는 조건을 달성할 수 있어요"
                : "더 기다리면 원하는 조건을 맞출 수 있어요";

        return Optional.of(GoalRecommendationResponse.RecommendationItem.builder()
                .type(AlgorithmType.HOLD_OUT)
                .title(title)
                .reason(buildReason(best.m))
                .condition(GoalRecommendationResponse.Condition.builder()
                        .regionCode(best.median.getRegionCode())
                        .regionName(best.median.getRegionName())
                        .housingType(best.candidate.housingType)
                        .dealType(best.candidate.dealType)
                        .areaMin(best.candidate.areaMin)
                        .areaMax(best.candidate.areaMax)
                        .monthlyRent(best.candidate.dealType == DealType.WOLSE
                                && best.median.getMonthlyRent() != null
                                && best.median.getMonthlyRent().getQ3() != null
                                ? best.median.getMonthlyRent().getQ3() : 0L)
                        .sampleCount(best.median.getSampleCount())
                        .build())
                .loanX(plans.getLoanX())
                .loanO(loanO)
                .build());
    }

    /**
     * 기준 조건에서 후보 목록을 생성한다.
     * housingType/dealType이 null이면 가능한 모든 타입 조합을 탐색한다.
     * 면적은 {@link RecommendationAlgorithm#SIZE_BUCKETS} 공유 버킷을 그대로 사용한다.
     * 보증금 범위는 시장 전체 시세를 발견하기 위해 0~1조로 고정한다.
     */
    private List<HousingCondition> generateCandidates(BaseCondition base) {
        List<HousingType> types = base.housingType != null
                ? List.of(base.housingType)
                : Arrays.asList(HousingType.values());
        List<DealType> deals = base.dealType != null
                ? List.of(base.dealType)
                : Arrays.asList(DealType.values());

        List<HousingCondition> candidates = new ArrayList<>();
        for (HousingType ht : types) {
            for (DealType dt : deals) {
                boolean isJeonse = dt == DealType.JEONSE;
                Long rentMin = isJeonse ? null : base.rentMin;
                Long rentMax = isJeonse ? null : (base.rentMax != null ? base.rentMax : DEFAULT_RENT_MAX);
                for (int[] size : SIZE_BUCKETS) {
                    candidates.add(new HousingCondition(
                            base.regionCode, ht, dt,
                            size[0], size[1],
                            0L, WIDE_DEPOSIT_MAX,
                            rentMin, rentMax));
                }
            }
        }
        return candidates;
    }

    /**
     * MC 투입 전 파이프라인: 시세 조회 → 표본 필터 → 면적 다운그레이드 → 가격 상한 필터.
     *
     * <p>가격 상한은 후보별 effectiveSaving(WOLSE면 월세 차감 후)으로 계산해
     * 실제 평가 기준과 일치시킨다. rentQ3가 없으면 baseSaving으로 근사한다.
     */
    private List<FetchedCandidate> buildPool(List<HousingCondition> candidates, BaseCondition base,
                                              AssetNetWorthBreakdown netWorth, long baseSaving, long horizon) {
        double baseMidArea = (base.areaMin != null && base.areaMax != null)
                ? (base.areaMin + base.areaMax) / 2.0 : 0;

        List<FetchedCandidate> pool = new ArrayList<>();
        for (HousingCondition candidate : candidates) {
            RentMedianResponse median = fetchAndValidateMedian(candidate);
            if (median == null) continue;

            double candidateMidArea = (candidate.areaMin + candidate.areaMax) / 2.0;
            if (base.areaMin != null && base.areaMax != null && candidateMidArea < baseMidArea) continue;

            long effectiveSaving = baseSaving;
            if (candidate.dealType == DealType.WOLSE) {
                Long rentQ3 = median.getMonthlyRent() != null ? median.getMonthlyRent().getQ3() : null;
                if (rentQ3 != null) effectiveSaving = Math.max(0, baseSaving - rentQ3);
            }
            if (median.getDeposit().getQ3() > budgetCalculator.calculate(netWorth, effectiveSaving, horizon)) continue;

            pool.add(new FetchedCandidate(candidate, median));
        }
        return pool;
    }

    /**
     * buildPool()을 통과한 후보를 평가해 스코어를 계산한다. 필터 탈락 시 null 반환.
     * 필터: 달성 가능 여부(MC 포함) → 추가 대기(m) 범위
     * 스코어: 0.35×cIS + 0.30×WP + 0.20×AM + 0.15×SP
     */
    private ScoredCandidate evaluate(FetchedCandidate fc, BaseCondition base,
                                     AssetNetWorthBreakdown netWorth, long baseSaving, long n,
                                     long maxExtra) {
        HousingCondition candidate = fc.candidate;
        RentMedianResponse median = fc.median;

        double candidateMidArea = (candidate.areaMin + candidate.areaMax) / 2.0;

        BudgetMetrics metrics = calcBudgetMetrics(candidate, median, netWorth, baseSaving, n);
        if (metrics == null) return null;

        if (metrics.m > maxExtra) return null;

        // AM: n+maxExtra 시점의 최대 예산 대비 여유 — 후보 가격이 쌀수록 높아져 변별력 있음
        // totalMonths <= n+maxExtra 이므로 maxBudget >= futureBudget >= futurePrice → AM >= 0
        long maxBudget = budgetCalculator.calculate(netWorth, metrics.effectiveSaving, n + maxExtra);
        double affordabilityMargin = (double)(maxBudget - metrics.futurePrice) / maxBudget;
        // 면적 미지정 시 cIS는 0.5 중립 고정 — baseMidArea=0으로 계산하면 모든 후보가 1.0에 수렴해 변별력 소실
        double condImprovScore = (base.areaMin == null || base.areaMax == null)
                ? 0.5
                : calcConditionImprovementScore((base.areaMin + base.areaMax) / 2.0, candidateMidArea);
        double waitPenalty     = 1.0 / (1.0 + metrics.m / 12.0);
        // successProbability: GBM 시뮬레이션 기반 도달 성공 확률. MC 실패 시 null → 0.0(불리 처리)
        double spScore = metrics.successProbability != null ? metrics.successProbability : 0.0;
        double score = 0.35 * condImprovScore
                     + 0.30 * waitPenalty
                     + 0.20 * affordabilityMargin
                     + 0.15 * spScore;

        log.debug("{} deposit={} effectiveSaving={} totalMonths={} m={} cIS={} WP={} AM={} SP={} score={}",
                candidate.label(), metrics.futurePrice, metrics.effectiveSaving, metrics.totalMonths, metrics.m,
                String.format("%.2f", condImprovScore), String.format("%.3f", waitPenalty),
                String.format("%.3f", affordabilityMargin),
                metrics.successProbability != null ? String.format("%.3f", metrics.successProbability) : "null",
                String.format("%.4f", score));

        return new ScoredCandidate(candidate, median, metrics.futurePrice, metrics.effectiveSaving, metrics.totalMonths, metrics.m, score);
    }

    /**
     * 후보의 시세를 조회하고 표본 수·Q3 존재 여부를 검증한다. 탈락 시 null 반환.
     */
    private RentMedianResponse fetchAndValidateMedian(HousingCondition candidate) {
        RentMedianResponse median;
        try {
            median = rentMedianService.getMedian(candidate.toMedianRequest());
        } catch (RuntimeException e) {
            log.warn("[HoldOut] 시세 조회 실패 {} : {}", candidate.label(), e.getMessage());
            return null;
        }
        if (median.getSampleCount() < MIN_SAMPLE_COUNT) return null;
        if (median.getDeposit().getQ3() == null) return null;
        return median;
    }

    /**
     * 시세와 자산 정보를 기반으로 예산 지표를 계산한다. 달성 불가(저축 불충분 등)이면 null 반환.
     */
    private BudgetMetrics calcBudgetMetrics(HousingCondition candidate, RentMedianResponse median,
                                             AssetNetWorthBreakdown netWorth, long baseSaving, long n) {
        long initialPrice = median.getDeposit().getQ3();

        long effectiveSaving = baseSaving;
        if (candidate.dealType == DealType.WOLSE) {
            Long rentQ3 = median.getMonthlyRent() != null ? median.getMonthlyRent().getQ3() : null;
            if (rentQ3 == null) return null;
            effectiveSaving = Math.max(0, baseSaving - rentQ3);
        }

        // 1단계: 현재 시세로 초기 도달 개월 추정 (MC 시작점)
        Long initialMonths = budgetCalculator.monthsToReach(netWorth, effectiveSaving, initialPrice);
        if (initialMonths == null) return null;
        if (initialMonths == 0) {
            return new BudgetMetrics(initialPrice, effectiveSaving, 0L, 0L, 1.0);
        }

        Long totalMonths = initialMonths;
        long futurePrice = initialPrice;
        Double successProbability = null;
        try {
            PriceModelRequest priceReq = candidate.toPriceModelRequest();
            for (int i = 0; i < MAX_MC_ITERATIONS; i++) {
                long budget = budgetCalculator.calculate(netWorth, effectiveSaving, totalMonths);
                MonteCarloEngine.Result mc = monteCarloService.simulate(
                        priceReq, initialPrice, budget, totalMonths.intValue());

                futurePrice = mc.priceP50();
                successProbability = mc.successProbability();

                Long nextMonths = budgetCalculator.monthsToReach(netWorth, effectiveSaving, futurePrice);
                if (nextMonths == null) return null;
                if (nextMonths.equals(totalMonths)) break;
                totalMonths = nextMonths;
            }
        } catch (RuntimeException e) {
            log.warn("[HoldOut] MC 실패, 현재 시세 폴백 {} : {}", candidate.label(), e.getMessage());
            totalMonths = initialMonths;
            futurePrice = initialPrice;
            successProbability = null;
        }

        long m = Math.max(0, totalMonths - n);
        return new BudgetMetrics(futurePrice, effectiveSaving, totalMonths, m, successProbability);
    }

    /**
     * CIS (Condition Improvement Score): 후보 면적이 기준 조건 대비 얼마나 개선되었는지 0.0~1.0으로 계산한다.
     *
     * <p>Hard Constraint(지역·거래유형·주거유형)는 generateCandidates에서 필터링되므로 포함하지 않는다.
     *
     * <p>공식: clamp(0.5 + delta / 20.0, 0.0, 1.0)
     * <ul>
     *   <li>delta = 0평 (기준과 동일 면적): 0.5</li>
     *   <li>delta = +5평 (한 버킷 업그레이드): 0.75</li>
     *   <li>delta = +10평 이상: 1.0 (최대)</li>
     * </ul>
     */
    private double calcConditionImprovementScore(double baseMidArea, double candidateMidArea) {
        double delta = candidateMidArea - baseMidArea;
        return Math.min(1.0, Math.max(0.0, 0.5 + delta / 20.0));
    }

    /**
     * request → housing 순으로 기준 조건과 목표 시점을 병합해 BaseCondition을 반환한다.
     */
    private BaseCondition resolveCondition(GoalRecommendationRequest req, GoalHousing housing, Goal activeGoal) {
        String regionCode = pick(req.getRegionCode(), housing != null ? housing.getRegionCode() : null);
        if (regionCode == null) return null;

        YearMonth targetDate = req.getTargetDate() != null ? req.getTargetDate()
                : (activeGoal != null && activeGoal.getTargetDate() != null)
                    ? YearMonth.from(activeGoal.getTargetDate()) : null;

        return new BaseCondition(
                regionCode,
                pick(req.getPropertyType(),  housing != null ? housing.getHousingType()    : null),
                pick(req.getTradeType(),      housing != null ? housing.getDealType()       : null),
                pick(req.getSizeMin(),        housing != null ? housing.getAreaMin()        : null),
                pick(req.getSizeMax(),        housing != null ? housing.getAreaMax()        : null),
                pick(req.getMonthlyRentMin(), housing != null ? housing.getMonthlyRentMin() : null),
                pick(req.getMonthlyRentMax(), housing != null ? housing.getMonthlyRentMax() : null),
                targetDate);
    }

    private String buildReason(long extraMonths) {
        if (extraMonths == 0) return "현재 계획대로 저축하면 원하는 조건에 도달할 수 있어요.";
        long years  = extraMonths / 12;
        long months = extraMonths % 12;
        if (years == 0)  return extraMonths + "개월 더 기다리면 원하는 조건의 집을 구할 수 있어요.";
        if (months == 0) return years + "년 더 기다리면 원하는 조건의 집을 구할 수 있어요.";
        return years + "년 " + months + "개월 더 기다리면 원하는 조건의 집을 구할 수 있어요.";
    }

    private <T> T pick(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    /** resolveCondition 결과 — regionCode 이외 필드는 null 허용 */
    private record BaseCondition(
            String regionCode, HousingType housingType, DealType dealType,
            Integer areaMin, Integer areaMax, Long rentMin, Long rentMax,
            YearMonth targetDate) { }

    private record HousingCondition(
            String regionCode, HousingType housingType, DealType dealType, int areaMin,
            int areaMax, long depositMin, long depositMax, Long monthlyRentMin,
            Long monthlyRentMax
    ) {
        String label() {
            return housingType + "/" + dealType + " area=[" + areaMin + "," + areaMax + "]";
        }

        PriceModelRequest toPriceModelRequest() {
            PriceModelRequest req = new PriceModelRequest();
            req.setRegionCode(regionCode);
            req.setHousingType(housingType);
            req.setDealType(dealType);
            req.setAreaMin(areaMin);
            req.setAreaMax(areaMax);
            return req;
        }

        RentMedianRequest toMedianRequest() {
            RentMedianRequest r = new RentMedianRequest();
            r.setRegionCode(regionCode);
            r.setHousingType(housingType);
            r.setDealType(dealType);
            r.setAreaMin(areaMin);
            r.setAreaMax(areaMax);
            r.setDepositMin(depositMin);
            r.setDepositMax(depositMax);
            if (dealType == DealType.WOLSE) {
                r.setMonthlyRentMin(monthlyRentMin);
                r.setMonthlyRentMax(monthlyRentMax);
            }
            return r;
        }
    }

    /** 시세 조회·표본·상한 필터를 통과한 후보 (MC 투입 전 단계) */
    private record FetchedCandidate(HousingCondition candidate, RentMedianResponse median) {}

    private record BudgetMetrics(long futurePrice, long effectiveSaving, long totalMonths, long m,
                                  Double successProbability) {}

    private record ScoredCandidate(
            HousingCondition candidate, RentMedianResponse median, long futurePrice,
            long effectiveSaving, long totalMonths, long m, double score) { }
}
