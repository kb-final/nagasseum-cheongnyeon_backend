package com.team.independence.goal.service.algorithm;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * "더 기다리면 더 넓은 평수로 이사할 수 있어요" 카드를 만드는 HoldOut 추천 알고리즘.
 *
 * <h3>핵심 가치</h3>
 * 사용자가 지정한 조건(주거유형·거래유형)은 그대로 유지하되,
 * {@link #PATIENCE_BONUS} 개월 더 인내하면 <b>더 넓은 평수</b>를 얻을 수 있음을 보여준다.
 * 주거유형·거래유형의 "더 좋다"는 기준은 주관적이지만, 평수는 크면 클수록 명확한 업그레이드다.
 *
 * <h3>평수 업그레이드 필터</h3>
 * 사용자가 평수를 지정했으면 {@link RecommendationAlgorithm#SIZE_BUCKETS} 중
 * {@code areaMin > base.areaMax} 인 버킷만 후보로 허용한다.
 * 같은 평수대를 반환하면 "더 기다려서 얻는 이득"이 없으므로 제외한다.
 * 평수를 지정하지 않은 경우에는 필터 없이 전체 버킷을 탐색하고 스코어링에서 큰 버킷을 선호한다.
 *
 * <h3>지역 확장 폴백</h3>
 * 요청 지역이 시군구(5자리)이고 업그레이드 후보가 없으면 상위 시도(2자리)로 확장해 재탐색한다.
 * 이때 "요청 지역은 예산 초과지만 인내하면 인근 지역에서 가능" 메시지를 내보낸다.
 *
 * <h3>기준 조건 결정 우선순위</h3>
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

    private static final int  MIN_SAMPLE_COUNT  = 10;
    private static final long WIDE_DEPOSIT_MAX  = 1_000_000_000_000L;
    private static final int  MAX_MC_ITERATIONS = 3;

    /** RentMedianServiceImpl과 동일한 집계 구간 */
    private static final int MONTHS = 6;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 추가 인내 허용 개월 수.
     * targetDate 이후(또는 targetDate 미지정 시 지금부터) 최대 48개월(4년)까지 탐색한다.
     */
    private static final long PATIENCE_BONUS   = 48;
    private static final long DEFAULT_RENT_MAX = 2_000_000L;

    private final LoanPlanCalculator loanPlanCalculator;
    private final BudgetCalculator   budgetCalculator;
    private final GoalMapper         goalMapper;
    private final GoalHousingMapper  goalHousingMapper;
    private final RentMedianService  rentMedianService;
    private final MonteCarloService  monteCarloService;

    @Override
    public List<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request, MemberFinancialContext ctx
    ) {
        Goal activeGoal = goalMapper.findActiveByMemberId(memberId);
        GoalHousing housing = (activeGoal != null)
                ? goalHousingMapper.findByGoalId(activeGoal.getId())
                : null;

        BaseCondition base = resolveCondition(request, housing, activeGoal);
        if (base == null) return List.of();

        YearMonth now = YearMonth.now();
        AssetNetWorthBreakdown netWorth = ctx.netWorth();
        long baseSaving = ctx.effectiveSaving();

        long n = base.targetDate != null
                ? Math.max(0, ChronoUnit.MONTHS.between(now, base.targetDate))
                : 0L;
        long window = n + PATIENCE_BONUS;

        String endYm   = now.format(YM);
        String startYm = now.minusMonths(MONTHS - 1).format(YM);

        // 1차: 요청 지역에서 평수 업그레이드 후보 탐색 (bulk 1회 쿼리)
        Map<String, RentMedianResponse> bulkMedians =
                rentMedianService.getBulkMedian(base.regionCode, startYm, endYm);
        List<FetchedCandidate> pool = buildPool(generateCandidates(base), base, netWorth, baseSaving, window, bulkMedians);
        boolean regionExpanded = false;

        // 2차: 시군구(5자리)로 요청했지만 pool이 비었으면 상위 시도(2자리)로 확장 (bulk 1회 추가)
        if (pool.isEmpty() && base.regionCode.length() > 2) {
            String sidoCode = base.regionCode.substring(0, 2);
            Map<String, RentMedianResponse> sidoBulkMedians =
                    rentMedianService.getBulkMedian(sidoCode, startYm, endYm);
            BaseCondition sidoBase = new BaseCondition(
                    sidoCode, base.housingType, base.dealType,
                    base.areaMin, base.areaMax, base.rentMin, base.rentMax, base.targetDate);
            pool = buildPool(generateCandidates(sidoBase), base, netWorth, baseSaving, window, sidoBulkMedians);
            regionExpanded = !pool.isEmpty();
        }

        ScoredCandidate best = null;
        for (FetchedCandidate fc : pool) {
            ScoredCandidate scored = evaluate(fc, base, netWorth, baseSaving, n);
            if (scored == null) continue;
            if (best == null || scored.score > best.score) best = scored;
        }

        if (best == null) {
            log.info("[HoldOut] 적합한 후보 없음 — soft-fail 반환 memberId={} regionCode={}", memberId, base.regionCode);
            return List.of(GoalRecommendationResponse.RecommendationItem.builder()
                    .type(AlgorithmType.HOLD_OUT)
                    .build());
        }

        log.info("[HoldOut] 최종 선택 memberId={} {} futurePrice={} m={}개월 score={} regionExpanded={}",
                memberId, best.candidate.label(),
                best.futurePrice, best.m, String.format("%.4f", best.score), regionExpanded);

        YearMonth targetDate = now.plusMonths(best.totalMonths);
        LoanPlans plans = loanPlanCalculator.calculate(memberId, best.futurePrice, targetDate);

        GoalRecommendationResponse.LoanOPlan loanO = plans.getLoanO();
        if (loanO != null) {
            Long shortened = loanPlanCalculator.calcShortenedMonths(
                    memberId, netWorth, best.effectiveSaving, best.futurePrice, best.totalMonths);
            loanO = loanO.toBuilder().shortenedMonths(shortened).build();
        }

        return List.of(GoalRecommendationResponse.RecommendationItem.builder()
                .type(AlgorithmType.HOLD_OUT)
                .condition(GoalRecommendationResponse.Condition.builder()
                        .regionCode(best.median.getRegionCode())
                        .regionName(best.median.getRegionName())
                        .housingType(best.candidate.housingType)
                        .dealType(best.candidate.dealType)
                        .areaMin(best.candidate.areaMin)
                        .areaMax(best.candidate.areaMax)
                        .depositMin(best.candidate.depositMin)
                        .depositMax(best.candidate.depositMax)
                        .monthlyRent(best.candidate.dealType == DealType.WOLSE
                                && best.median.getMonthlyRent() != null
                                && best.median.getMonthlyRent().getQ3() != null
                                ? best.median.getMonthlyRent().getQ3() : 0L)
                        .sampleCount(best.median.getSampleCount())
                        .marketMedianAmount(best.futurePrice)
                        .build())
                .loanX(plans.getLoanX())
                .loanO(loanO)
                .build());
    }

    /**
     * 기준 조건에서 후보 목록을 생성한다.
     * housingType/dealType이 null이면 가능한 모든 타입 조합을 탐색한다.
     * 면적은 {@link RecommendationAlgorithm#SIZE_BUCKETS} 공유 버킷을 그대로 사용한다.
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
     * 시세 조회 → 평수 업그레이드 필터 → 예산 상한 필터.
     *
     * <p><b>평수 업그레이드 필터</b>: 사용자가 지정한 {@code base.areaMax}보다
     * {@code areaMin}이 큰 버킷만 허용한다. 같은 평수대를 반환하면 "더 기다려서 더 넓게"라는
     * HoldOut의 가치를 전달할 수 없기 때문이다.
     * 평수 미지정 시에는 필터 없이 전체 버킷을 탐색한다.
     *
     * <p>지역 확장 시에도 {@code base}는 항상 사용자의 원래 요청 기준을 사용한다.
     * 평수 기준은 지역이 바뀌어도 변하지 않는다.
     */
    private List<FetchedCandidate> buildPool(List<HousingCondition> candidates, BaseCondition base,
                                              AssetNetWorthBreakdown netWorth, long baseSaving, long horizon,
                                              Map<String, RentMedianResponse> bulkMedians) {
        List<FetchedCandidate> pool = new ArrayList<>();
        for (HousingCondition candidate : candidates) {
            // 평수 업그레이드 필터: 요청 최대 평수(areaMax) 이하 버킷은 업그레이드가 아니므로 제외
            if (base.areaMin != null && base.areaMax != null && candidate.areaMin <= base.areaMax) continue;

            String key = candidate.housingType + "|" + candidate.dealType + "|" + candidate.areaMin;
            RentMedianResponse median = bulkMedians.get(key);
            if (median == null || median.getSampleCount() < MIN_SAMPLE_COUNT) continue;
            if (median.getDeposit().getQ3() == null) continue;

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
     * 스코어: 0.35×cIS + 0.30×WP + 0.20×AM + 0.15×SP
     * m > PATIENCE_BONUS 이면 null 반환 (MC 수렴 후 실제 도달 개월이 window를 넘는 경우).
     */
    private ScoredCandidate evaluate(FetchedCandidate fc, BaseCondition base,
                                     AssetNetWorthBreakdown netWorth, long baseSaving, long n) {
        HousingCondition candidate = fc.candidate;
        RentMedianResponse median = fc.median;

        double candidateMidArea = (candidate.areaMin + candidate.areaMax) / 2.0;

        BudgetMetrics metrics = calcBudgetMetrics(candidate, median, netWorth, baseSaving, n);
        if (metrics == null) return null;
        if (metrics.m > PATIENCE_BONUS) return null;

        long maxBudget = budgetCalculator.calculate(netWorth, metrics.effectiveSaving, n + PATIENCE_BONUS);
        double affordabilityMargin = (double)(maxBudget - metrics.futurePrice) / maxBudget;
        double condImprovScore = (base.areaMin == null || base.areaMax == null)
                ? 0.5
                : calcConditionImprovementScore((base.areaMin + base.areaMax) / 2.0, candidateMidArea);
        double waitPenalty = 1.0 / (1.0 + metrics.m / 12.0);
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

        return new ScoredCandidate(candidate, median, metrics.futurePrice, metrics.effectiveSaving,
                metrics.totalMonths, metrics.m, score);
    }

    private BudgetMetrics calcBudgetMetrics(HousingCondition candidate, RentMedianResponse median,
                                             AssetNetWorthBreakdown netWorth, long baseSaving, long n) {
        long initialPrice = median.getDeposit().getQ3();

        long effectiveSaving = baseSaving;
        if (candidate.dealType == DealType.WOLSE) {
            Long rentQ3 = median.getMonthlyRent() != null ? median.getMonthlyRent().getQ3() : null;
            if (rentQ3 == null) return null;
            effectiveSaving = Math.max(0, baseSaving - rentQ3);
        }

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

    private double calcConditionImprovementScore(double baseMidArea, double candidateMidArea) {
        double delta = candidateMidArea - baseMidArea;
        return Math.min(1.0, Math.max(0.0, 0.5 + delta / 20.0));
    }

    // ─── resolveCondition ────────────────────────────────────────────────────────

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

    private <T> T pick(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    // ─── 내부 타입 ────────────────────────────────────────────────────────────────

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

    private record FetchedCandidate(HousingCondition candidate, RentMedianResponse median) {}

    private record BudgetMetrics(long futurePrice, long effectiveSaving, long totalMonths, long m,
                                  Double successProbability) {}

    private record ScoredCandidate(
            HousingCondition candidate, RentMedianResponse median, long futurePrice,
            long effectiveSaving, long totalMonths, long m, double score) { }
}
