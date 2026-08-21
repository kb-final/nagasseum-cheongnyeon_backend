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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Realistic 결과를 기준점으로 삼아 "가격순 바로 위 칸"을 추천하는 HoldOut 알고리즘.
 *
 * <h3>컨셉</h3>
 * Realistic이 "지금 저축으로 목표 시점 안에 가능한 조건"을 찾아주면,
 * HoldOut은 그 결과를 기준점(baseline)으로 삼아 <b>조금 더 비싼 다음 등급</b>을 찾는다.
 * "조금 더 기다리면 한 단계 나은 집을 얻을 수 있다"는 메시지를 만드는 카드다.
 *
 * <h3>후보군: 40개 조합 전체</h3>
 * 평수 하나만 키우거나 주거유형만 올리는 식으로 변수를 하나씩 바꾸면, 다른 조합이 더 싼 다음 등급인데도
 * 못 보고 지나칠 수 있다. 그래서 주거유형(4) × 거래유형(2) × 평수 버킷(5) = 40개 조합을 모두 실거래
 * 가격으로 평가하고, baseline보다 <b>비싼 것 중 가장 싼 것</b>을 고른다. "다음 등급이 무엇인지"는
 * 우리가 정하지 않고 시장 가격이 정한다. baseline과 같은 조합은 자연히 후보에서 제외된다
 * (자기 자신보다 비쌀 수 없으므로).
 *
 * <h3>도달 개월 상한을 두지 않는다</h3>
 * 다음 등급이 baseline 바로 위 칸이라 가격 점프가 이미 최소이므로, 추가로 대기 개월을 잘라낼 필요가
 * 없다. 계산된 결과가 아무리 오래 걸리더라도 그대로 보여준다.
 *
 * <h3>baseline이 최상위 조합일 때</h3>
 * 40개 조합을 다 훑어도 baseline보다 비싼 것이 없으면(이미 최고가 조합이면) soft-fail 카드
 * ({@code condition = null})를 반환한다. baseline을 그대로 복제해 보여주지 않는다 — "다음 등급"이라는
 * 이 카드의 정체성에 맞는 답이 없다는 뜻이라, 없는 답을 지어내지 않는다.
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

        long desiredMonths = request.getTargetDate() != null
                ? RecommendationAlgorithm.monthsUntil(request.getTargetDate())
                : 24L;

        YearMonth now = YearMonth.now();
        String endYm   = now.format(YM);
        String startYm = now.minusMonths(MONTHS - 1).format(YM);
        Map<String, RentMedianResponse> bulkMedians =
                rentMedianService.getBulkMedian(baseline.getRegionCode(), startYm, endYm);

        // 목표 시점(desiredMonths)의 예산은 후보와 무관하게 같으므로 루프 밖에서 한 번만 계산한다.
        long budgetAtT = budgetCalculator.calculate(
                ctx.netWorth(), ctx.rawMonthlySaving(), ctx.loanSchedules(), desiredMonths);

        // 40개 조합(주거유형 × 거래유형 × 평수 버킷)을 모두 평가해 baseline보다 비싼 것 중 가장 싼 것을 고른다.
        EvaluatedCandidate nextRung = Arrays.stream(HousingType.values())
                .flatMap(ht -> Arrays.stream(DealType.values())
                        .flatMap(dt -> Arrays.stream(SIZE_BUCKETS)
                                .map(size -> evaluate(baseline.getRegionCode(), ht, dt, size, bulkMedians, budgetAtT, desiredMonths))))
                .filter(c -> c != null && c.comparableAmount() > baselineComparable)
                .min(Comparator.comparingLong(EvaluatedCandidate::comparableAmount))
                .orElse(null);

        if (nextRung == null) {
            return List.of(emptyCard());
        }

        LoanPlans plans = loanPlanCalculator.calculateSavingFixed(
                memberId, nextRung.deposit(), ctx.netWorth(), ctx.rawMonthlySaving(), ctx.loanSchedules());

        return List.of(GoalRecommendationResponse.RecommendationItem.builder()
                .type(AlgorithmType.HOLD_OUT)
                .condition(GoalRecommendationResponse.Condition.builder()
                        .regionCode(nextRung.regionCode())
                        .regionName(nextRung.regionName())
                        .housingType(nextRung.housingType())
                        .dealType(nextRung.dealType())
                        .areaMin(nextRung.areaMin())
                        .areaMax(nextRung.areaMax())
                        .depositMin(0L)
                        .depositMax(DEPOSIT_MAX_DISPLAY)
                        .monthlyRent(nextRung.monthlyRent())
                        .sampleCount(nextRung.sampleCount())
                        .marketMedianAmount(nextRung.deposit())
                        .build())
                .loanX(plans.getLoanX())
                .loanO(plans.getLoanO())
                .build());
    }

    // ─── 후보 평가 ────────────────────────────────────────────────────────────────

    /**
     * 조합 하나(주거유형·거래유형·평수 버킷)의 실거래 median을 조회해 목표 시점의 투영 가격까지 계산한다.
     * 표본 부족이거나 실거래 데이터가 없으면 null.
     */
    private EvaluatedCandidate evaluate(
            String regionCode, HousingType housingType, DealType dealType, int[] size,
            Map<String, RentMedianResponse> bulkMedians, long budgetAtT, long desiredMonths) {

        int areaMin = size[0];
        int areaMax = size[1];
        String key = housingType + "|" + dealType + "|" + areaMin;
        RentMedianResponse median = bulkMedians.get(key);
        if (median == null || median.getSampleCount() < MIN_SAMPLE_COUNT) return null;
        if (median.getDeposit() == null || median.getDeposit().getMedian() == null) return null;

        long deposit = median.getDeposit().getMedian();
        long monthlyRent = 0L;
        if (dealType == DealType.WOLSE) {
            Long rentMedian = median.getMonthlyRent() != null ? median.getMonthlyRent().getMedian() : null;
            if (rentMedian == null) return null;
            monthlyRent = rentMedian;
        }

        // MC로 목표 시점의 예상 가격 투영 — Realistic과 동일한 1회 시뮬레이션
        long projectedDeposit = deposit;
        try {
            MonteCarloEngine.Result mc = monteCarloService.simulate(
                    RecommendationAlgorithm.buildPriceModelRequest(regionCode, housingType, dealType, areaMin, areaMax),
                    deposit, budgetAtT, (int) desiredMonths);
            projectedDeposit = mc.priceP50();
        } catch (RuntimeException e) {
            log.debug("MC 실패, 현재 시세 사용. housingType={}, dealType={}", housingType, dealType);
        }

        long comparableAmount = toComparableAmount(projectedDeposit, monthlyRent);

        return new EvaluatedCandidate(
                regionCode, median.getRegionName(), housingType, dealType,
                areaMin, areaMax, projectedDeposit, monthlyRent,
                median.getSampleCount(), comparableAmount);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────────

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

    /** 평가가 끝난 조합 하나. {@code deposit}은 목표 시점 투영가, {@code comparableAmount}는 전세 환산 후 정렬용 값. */
    private record EvaluatedCandidate(
            String regionCode, String regionName, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, long deposit, long monthlyRent,
            int sampleCount, long comparableAmount) {}
}
