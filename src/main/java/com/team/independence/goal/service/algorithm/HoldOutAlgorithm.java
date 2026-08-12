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
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.goal.service.RecommendationAlgorithm;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
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
    private static final int  AREA_STEP        = 5;
    private static final long WIDE_DEPOSIT_MAX = 1_000_000_000_000L;

    /**
     * 추가 대기 허용 범위 (개월).
     * n=0이어도 최소 24개월(2년)까지는 HoldOut 후보로 허용하고,
     * 어떤 상황에서도 36개월(3년)을 초과하는 대기는 추천하지 않는다.
     */
    private static final long MIN_EXTRA_MONTHS = 24;
    private static final long MAX_EXTRA_MONTHS = 36;

    /** housingType/dealType/면적 조건이 없을 때의 탐색 기본값 */
    private static final int  DEFAULT_AREA_MIN = 10;
    private static final int  DEFAULT_AREA_MAX = 30;
    private static final long DEFAULT_RENT_MAX = 2_000_000L;

    /**
     * 후보 탐색 면적 확장 범위.
     * 기준 areaMin 아래 한 버킷, areaMax 위 20평까지 넓힌다.
     * 사용자가 지정한 범위 외에도 가까운 가격대의 매물을 발견하기 위함이다.
     */
    private static final int AREA_EXPAND_BELOW = AREA_STEP;
    private static final int AREA_EXPAND_ABOVE = 20;

    private final LoanPlanCalculator  loanPlanCalculator;
    private final BudgetCalculator    budgetCalculator;
    private final GoalMapper          goalMapper;
    private final GoalHousingMapper   goalHousingMapper;
    private final AssetSummaryService assetSummaryService;
    private final RentMedianService   rentMedianService;

    @Override
    public Optional<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request) {

        Goal activeGoal = goalMapper.findActiveByMemberId(memberId);
        GoalHousing housing = (activeGoal != null)
                ? goalHousingMapper.findByGoalId(activeGoal.getId())
                : null;

        BaseCondition base = resolveCondition(request, housing, activeGoal);
        if (base == null) return Optional.empty();

        YearMonth now = YearMonth.now(); // 기준 시점 통일
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long monthlySaving = resolveMonthlySaving(memberId);
        long n = base.targetDate != null ? Math.max(0, ChronoUnit.MONTHS.between(now, base.targetDate)) : 0;

        long maxExtra = Math.min(Math.max(n, MIN_EXTRA_MONTHS), MAX_EXTRA_MONTHS);

        ScoredCandidate best = null;
        ScoredCandidate bestAlreadyAchievable = null;
        for (HousingCondition candidate : generateCandidates(base)) {
            ScoredCandidate scored = evaluate(candidate, base, netWorth, monthlySaving, n, maxExtra);
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
                        .build())
                .loanX(plans.getLoanX())
                .loanO(plans.getLoanO())
                .build());
    }

    /**
     * 기준 조건에서 5평 단위 면적 버킷 후보 목록을 생성한다.
     * housingType/dealType이 null이면 가능한 모든 타입 조합을 탐색한다.
     * 보증금 범위는 시장 전체 시세를 발견하기 위해 0~1조로 고정한다.
     */
    private List<HousingCondition> generateCandidates(BaseCondition base) {
        List<HousingType> types = base.housingType != null
                ? List.of(base.housingType)
                : Arrays.asList(HousingType.values());
        List<DealType> deals = base.dealType != null
                ? List.of(base.dealType)
                : Arrays.asList(DealType.values());

        int areaMin = base.areaMin != null ? base.areaMin : DEFAULT_AREA_MIN;
        int areaMax = base.areaMax != null ? base.areaMax : DEFAULT_AREA_MAX;
        int start = Math.max(AREA_STEP, areaMin - AREA_EXPAND_BELOW);
        int end   = areaMax + AREA_EXPAND_ABOVE;

        List<HousingCondition> candidates = new ArrayList<>();
        for (HousingType ht : types) {
            for (DealType dt : deals) {
                boolean isJeonse = dt == DealType.JEONSE;
                Long rentMin = isJeonse ? null : base.rentMin;
                Long rentMax = isJeonse ? null : (base.rentMax != null ? base.rentMax : DEFAULT_RENT_MAX);
                for (int lo = start; lo < end; lo += AREA_STEP) {
                    candidates.add(new HousingCondition(
                            base.regionCode, ht, dt,
                            lo, lo + AREA_STEP,
                            0L, WIDE_DEPOSIT_MAX,
                            rentMin, rentMax));
                }
            }
        }
        return candidates;
    }

    /**
     * 후보 하나를 평가해 스코어를 계산한다. 필터 탈락 시 null 반환.
     * 필터 순서: 표본 수 → 보증금 Q3 → 면적 다운그레이드 → 달성 가능 여부 → 추가 대기(m) 범위
     * 스코어: 0.40×cIS + 0.35×WP + 0.25×AM
     */
    private ScoredCandidate evaluate(HousingCondition candidate, BaseCondition base,
                                     AssetNetWorthBreakdown netWorth, long monthlySaving, long n,
                                     long maxExtra) {
        RentMedianResponse median = fetchAndValidateMedian(candidate);
        if (median == null) return null;

        // Soft filter: 희망 면적 중간값보다 작은 후보(다운그레이드) 제외
        // Hard Constraint(지역·거래유형·주거유형)는 generateCandidates에서 이미 필터링된 상태
        int bMin = base.areaMin != null ? base.areaMin : DEFAULT_AREA_MIN;
        int bMax = base.areaMax != null ? base.areaMax : DEFAULT_AREA_MAX;
        double baseMidArea      = (bMin + bMax) / 2.0;
        double candidateMidArea = (candidate.areaMin + candidate.areaMax) / 2.0;
        if (candidateMidArea < baseMidArea) {
            log.debug("{} → 제외 (면적 다운그레이드: 후보 {}평 < 기준 {}평)",
                    candidate.label(), candidateMidArea, baseMidArea);
            return null;
        }

        BudgetMetrics metrics = calcBudgetMetrics(candidate, median, netWorth, monthlySaving, n);
        if (metrics == null) return null;

        if (metrics.m > maxExtra) {
            log.debug("{} → 제외 (m={}, 허용범위 0~{})", candidate.label(), metrics.m, maxExtra);
            return null;
        }

        // AM: n+maxExtra 시점의 최대 예산 대비 여유 — 후보 가격이 쌀수록 높아져 변별력 있음
        // totalMonths <= n+maxExtra 이므로 maxBudget >= futureBudget >= futurePrice → AM >= 0
        long maxBudget = budgetCalculator.calculate(netWorth, metrics.effectiveSaving, n + maxExtra);
        double affordabilityMargin = (double)(maxBudget - metrics.futurePrice) / maxBudget;
        double condImprovScore = calcConditionImprovementScore(baseMidArea, candidateMidArea);
        double waitPenalty     = 1.0 / (1.0 + metrics.m / 12.0);
        double score = 0.40 * condImprovScore
                     + 0.35 * waitPenalty
                     + 0.25 * affordabilityMargin;

        log.debug("{} deposit={} effectiveSaving={} totalMonths={} m={} cIS={} WP={} AM={} score={}",
                candidate.label(), metrics.futurePrice, metrics.effectiveSaving, metrics.totalMonths, metrics.m,
                String.format("%.2f", condImprovScore), String.format("%.3f", waitPenalty),
                String.format("%.3f", affordabilityMargin), String.format("%.4f", score));

        return new ScoredCandidate(candidate, median, metrics.futurePrice, metrics.totalMonths, metrics.m, score);
    }

    /**
     * 후보의 시세를 조회하고 표본 수·Q3 존재 여부를 검증한다. 탈락 시 null 반환.
     */
    private RentMedianResponse fetchAndValidateMedian(HousingCondition candidate) {
        RentMedianResponse median;
        try {
            median = rentMedianService.getMedian(candidate.toMedianRequest());
        } catch (Exception e) {
            log.debug("{} → 제외 (가격 조회 실패: {})", candidate.label(), e.getMessage());
            return null;
        }
        if (median.getSampleCount() < MIN_SAMPLE_COUNT) {
            log.debug("{} → 제외 (표본 부족: {}건)", candidate.label(), median.getSampleCount());
            return null;
        }
        if (median.getDeposit().getQ3() == null) {
            log.debug("{} → 제외 (보증금 Q3 없음)", candidate.label());
            return null;
        }
        return median;
    }

    /**
     * 시세와 자산 정보를 기반으로 예산 지표를 계산한다. 달성 불가(저축 불충분 등)이면 null 반환.
     */
    private BudgetMetrics calcBudgetMetrics(HousingCondition candidate, RentMedianResponse median,
                                             AssetNetWorthBreakdown netWorth, long monthlySaving, long n) {
        // TODO: 현재는 최근 실거래 Q3를 그대로 사용하지만, totalMonths개월 후 시세는 다를 수 있다.
        //       RentMedianService에 시계열 기반 미래 시세 예측이 추가되면,
        //       targetYearMonth(= now.plusMonths(totalMonths))를 candidate에 포함해 예측값을 사용해야 한다.
        //       현재는 예산(미래값) vs 가격(현재값)의 비대칭이 존재한다.
        long futurePrice = median.getDeposit().getQ3();

        long effectiveSaving = monthlySaving;
        if (candidate.dealType == DealType.WOLSE) {
            Long rentQ3 = median.getMonthlyRent() != null ? median.getMonthlyRent().getQ3() : null;
            if (rentQ3 == null) {
                log.debug("{} → 제외 (월세 Q3 없음 — effectiveSaving 추정 불가)", candidate.label());
                return null;
            }
            effectiveSaving = Math.max(0, monthlySaving - rentQ3);
        }

        Long totalMonths = budgetCalculator.monthsToReach(netWorth, effectiveSaving, futurePrice);
        if (totalMonths == null) {
            log.debug("{} → 제외 (달성 불가: deposit={} effectiveSaving={})",
                    candidate.label(), futurePrice, effectiveSaving);
            return null;
        }

        long m = Math.max(0, totalMonths - n);
        return new BudgetMetrics(futurePrice, effectiveSaving, totalMonths, m);
    }

    /**
     * CIS (Condition Improvement Score): 후보 면적이 기준 조건 대비 얼마나 개선되었는지 0.0~1.0으로 계산한다.
     *
     * <p>Hard Constraint(지역·거래유형·주거유형)는 generateCandidates에서 필터링되므로 포함하지 않는다.
     * 다운그레이드 후보(candidateMidArea &lt; baseMidArea)는 evaluate에서 이미 제외되므로 delta &ge; 0이 보장된다.
     * 가격 개선(가격 &darr;)은 AM(Affordability Margin)이 담당하므로 여기서는 면적만 평가한다.
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
     * regionCode만 필수이며, 나머지가 null이면 generateCandidates에서 기본값 또는 전체 탐색으로 보완한다.
     * regionCode를 확보할 수 없으면 null 반환.
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

    /** 현재 자산 연동 기반의 월 저축액. 0이면 기존 자산만으로 달성 가능한 후보만 살아남는다. */
    private long resolveMonthlySaving(long memberId) {
        Long savings = assetSummaryService.getSummary(memberId).getMonthlySavings();
        return savings != null ? savings : 0L;
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

    private record BudgetMetrics(long futurePrice, long effectiveSaving, long totalMonths, long m) {}

    private record ScoredCandidate(
            HousingCondition candidate, RentMedianResponse median, long futurePrice,
            long totalMonths, long m, double score) { }
}
