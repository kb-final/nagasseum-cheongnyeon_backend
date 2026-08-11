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
import com.team.independence.goal.service.BudgetCalculator;
import com.team.independence.goal.service.LoanPlanCalculator;
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
 * 조건 일치(cMS)·대기 패널티(WP)·여유 자산(AM) 스코어로 비교해 가장 높은 점수의 후보를 추천한다.
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

        BaseCondition base = resolveCondition(request, housing);
        if (base == null) return Optional.empty();

        YearMonth now = YearMonth.now(); // 기준 시점 통일 — 이후 계산에서 재사용
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long monthlySaving = resolveMonthlySaving(memberId);
        long n = calcRemainingMonths(request, activeGoal, now);

        long maxExtra = Math.min(Math.max(n, MIN_EXTRA_MONTHS), MAX_EXTRA_MONTHS);

        List<String> evalLogs = new ArrayList<>();
        ScoredCandidate best = null;
        ScoredCandidate bestAlreadyAchievable = null;
        for (HousingCondition candidate : generateCandidates(base)) {
            ScoredCandidate scored = evaluate(candidate, base, netWorth, monthlySaving, n, maxExtra, evalLogs);
            if (scored == null) continue;
            if (scored.m == 0) {
                if (bestAlreadyAchievable == null || scored.score > bestAlreadyAchievable.score) bestAlreadyAchievable = scored;
            } else {
                if (best == null || scored.score > best.score) best = scored;
            }
        }

        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("\n[HoldOut] 후보 평가 요약 memberId=").append(memberId);
            evalLogs.forEach(line -> sb.append("\n  ").append(line));
            log.debug(sb.toString());
        }

        // HoldOut 범위(1~maxExtra) 후보가 없으면 현재 계획으로 이미 달성 가능한 후보로 대체
        boolean isAlreadyAchievable = (best == null);
        if (isAlreadyAchievable) {
            best = bestAlreadyAchievable;
        }
        if (best == null) {
            return Optional.empty();
        }

        log.info("[HoldOut] 최종 선택 memberId={} {}/{} area=[{},{}] futurePrice={} m={}개월 score={} alreadyAchievable={}",
                memberId,
                best.candidate.housingType, best.candidate.dealType,
                best.candidate.areaMin, best.candidate.areaMax,
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
     * 후보 하나를 평가해 스코어를 계산한다. 필터 탈락 시 null 반환. 평가 내역은 evalLogs에 누적.
     * 필터 순서: 표본 수 → 보증금 Q3 → 달성 가능 여부 → 추가 대기(m) 범위
     * 스코어: 0.40×cMS + 0.35×WP + 0.25×AM
     */
    private ScoredCandidate evaluate(HousingCondition candidate, BaseCondition base,
                                     AssetNetWorthBreakdown netWorth, long monthlySaving, long n,
                                     long maxExtra, List<String> evalLogs) {
        String label = candidate.housingType + "/" + candidate.dealType
                + " area=[" + candidate.areaMin + "," + candidate.areaMax + "]";

        RentMedianResponse median;
        try {
            median = rentMedianService.getMedian(candidate.toMedianRequest());
        } catch (Exception e) {
            evalLogs.add(label + " → 제외 (가격 조회 실패: " + e.getMessage() + ")");
            return null;
        }

        if (median.getSampleCount() < MIN_SAMPLE_COUNT) {
            evalLogs.add(label + " → 제외 (표본 부족: " + median.getSampleCount() + "건)");
            return null;
        }
        Long depositQ3 = median.getDeposit().getQ3();
        if (depositQ3 == null) {
            evalLogs.add(label + " → 제외 (보증금 Q3 없음)");
            return null;
        }
        long futurePrice = depositQ3;

        // 월세 후보: 월세만큼 실질 저축 여력이 줄어드는 것으로 반영
        long effectiveSaving = monthlySaving;
        if (candidate.dealType == DealType.WOLSE) {
            Long rentQ3 = median.getMonthlyRent() != null ? median.getMonthlyRent().getQ3() : null;
            if (rentQ3 != null) {
                effectiveSaving = Math.max(0, monthlySaving - rentQ3);
            }
        }

        Long totalMonths = budgetCalculator.monthsToReach(netWorth, effectiveSaving, futurePrice);
        if (totalMonths == null) {
            evalLogs.add(String.format("%s → 제외 (달성 불가: deposit=%,d effectiveSaving=%,d)",
                    label, futurePrice, effectiveSaving));
            return null;
        }

        long futureBudget = budgetCalculator.calculate(netWorth, effectiveSaving, totalMonths);
        if (futureBudget < futurePrice) {
            evalLogs.add(String.format("%s → 제외 (예산 부족: budget=%,d price=%,d)", label, futureBudget, futurePrice));
            return null;
        }

        long m = Math.max(0, totalMonths - n);

        // m>maxExtra: 너무 긴 대기 → 현실적이지 않음 (m=0은 bestAlreadyAchievable 후보로 올려보냄)
        if (m > maxExtra) {
            evalLogs.add(String.format("%s → 제외 (m=%d, 허용범위 0~%d)", label, m, maxExtra));
            return null;
        }

        // AM: n+maxExtra 시점의 최대 예산 대비 여유 — 후보 가격이 쌀수록 높아져 변별력 있음
        // totalMonths <= n+maxExtra 이므로 maxBudget >= futureBudget >= futurePrice → AM >= 0
        long maxBudget = budgetCalculator.calculate(netWorth, effectiveSaving, n + maxExtra);
        double affordabilityMargin = (double)(maxBudget - futurePrice) / maxBudget;

        double condMatchScore = calcConditionMatchScore(base, candidate);
        double waitPenalty    = 1.0 / (1.0 + m / 12.0);

        double score = 0.40 * condMatchScore
                     + 0.35 * waitPenalty
                     + 0.25 * affordabilityMargin;

        evalLogs.add(String.format(
                "%s deposit=%,d effectiveSaving=%,d totalMonths=%d m=%d cMS=%.2f WP=%.3f AM=%.3f score=%.4f",
                label, futurePrice, effectiveSaving, totalMonths, m,
                condMatchScore, waitPenalty, affordabilityMargin, score));

        return new ScoredCandidate(candidate, median, futurePrice, totalMonths, m, score);
    }

    /**
     * 후보 조건이 기준 조건(BaseCondition)과 얼마나 일치하는지 0.0~1.0으로 계산한다.
     * 조건이 null이면 무관심으로 간주해 만점 처리한다.
     *
     * <p>주거유형(0.5) + 면적 겹침 비율(0.5). 지역은 모든 후보가 동일 지역이므로 포함하지 않는다.
     * 면적은 희망 범위[areaMin, areaMax]와 후보 버킷의 겹치는 구간 비율로 부분 점수를 부여한다.
     */
    private double calcConditionMatchScore(BaseCondition base, HousingCondition candidate) {
        double score = 0.0;

        // 주거 유형 (0.5)
        if (base.housingType == null || base.housingType == candidate.housingType) {
            score += 0.5;
        }

        // 면적 겹침 비율 (0.5) — binary 판정 대신 겹치는 구간으로 부분 점수 부여
        if (base.areaMin == null && base.areaMax == null) {
            score += 0.5;
        } else {
            int bMin = base.areaMin != null ? base.areaMin : 0;
            int bMax = base.areaMax != null ? base.areaMax : 999;
            int overlapStart = Math.max(candidate.areaMin, bMin);
            int overlapEnd   = Math.min(candidate.areaMax, bMax);
            if (overlapStart < overlapEnd) {
                score += 0.5 * (double)(overlapEnd - overlapStart) / AREA_STEP;
            }
        }

        return score;
    }

    /**
     * request → housing 순으로 기준 조건을 병합해 BaseCondition을 반환한다.
     * regionCode만 필수이며, 나머지가 null이면 generateCandidates에서 기본값 또는 전체 탐색으로 보완한다.
     * regionCode를 확보할 수 없으면 null 반환.
     */
    private BaseCondition resolveCondition(GoalRecommendationRequest req, GoalHousing housing) {
        String regionCode = pick(req.getRegionCode(), housing != null ? housing.getRegionCode() : null);
        if (regionCode == null) return null;

        return new BaseCondition(
                regionCode,
                pick(req.getPropertyType(),  housing != null ? housing.getHousingType()    : null),
                pick(req.getTradeType(),      housing != null ? housing.getDealType()       : null),
                pick(req.getSizeMin(),        housing != null ? housing.getAreaMin()        : null),
                pick(req.getSizeMax(),        housing != null ? housing.getAreaMax()        : null),
                pick(req.getMonthlyRentMin(), housing != null ? housing.getMonthlyRentMin() : null),
                pick(req.getMonthlyRentMax(), housing != null ? housing.getMonthlyRentMax() : null));
    }

    /** 현재 자산 연동 기반의 월 저축액. 0이면 기존 자산만으로 달성 가능한 후보만 살아남는다. */
    private long resolveMonthlySaving(long memberId) {
        Long savings = assetSummaryService.getSummary(memberId).getMonthlySavings();
        return savings != null ? savings : 0L;
    }

    /**
     * 현재 시점(now)으로부터 목표 시점까지 남은 개월 수(n)를 반환한다.
     * 우선순위: request.targetDate > 활성 목표의 targetDate > 0
     */
    private long calcRemainingMonths(GoalRecommendationRequest request, Goal activeGoal, YearMonth now) {
        if (request.getTargetDate() != null) {
            return Math.max(0, ChronoUnit.MONTHS.between(now, request.getTargetDate()));
        }
        if (activeGoal != null && activeGoal.getTargetDate() != null) {
            return Math.max(0, ChronoUnit.MONTHS.between(now, YearMonth.from(activeGoal.getTargetDate())));
        }
        return 0;
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
            Integer areaMin, Integer areaMax, Long rentMin, Long rentMax) { }

    private record HousingCondition(
            String regionCode, HousingType housingType, DealType dealType, int areaMin,
            int areaMax, long depositMin, long depositMax, Long monthlyRentMin,
            Long monthlyRentMax
    ) {
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

    private record ScoredCandidate(
            HousingCondition candidate, RentMedianResponse median, long futurePrice,
            long totalMonths, long m, double score) { }
}
