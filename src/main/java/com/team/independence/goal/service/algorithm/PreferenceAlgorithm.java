package com.team.independence.goal.service.algorithm;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
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
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.service.RentMedianService;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 선호 우선 추천 — 사용자가 입력한 조건을 그대로 돌려준다.
 *
 * <p>이 카드의 정체성은 <b>계산하지 않는 것</b>이다. 원한 조건의 실거래 시세를 그대로 보여주고,
 * 그것을 감당할 수 있는지는 판단하지 않는다. 30년이 걸릴 조건이어도 그대로 낸다. 감당 여부를 따지는
 * 일은 {@code REALISTIC}이, 시점을 늘려 도달 가능성을 보는 일은 {@code HOLD_OUT}이 한다.
 *
 * <p>{@code REALISTIC}과의 차이는 "조건을 몇 개 받았느냐"가 아니라 <b>빈 칸을 채울 때 계산을 하느냐</b>다.
 * 둘 다 빈 칸을 채우지만, 이 카드는 고정된 기본값 하나로 채우고 끝내는 반면 REALISTIC은 저축 여력에
 * 맞는 값을 탐색해 채운다. 그래서 사용자가 조건을 하나만 줘도 두 카드의 답이 갈린다.
 *
 * <p>기본값을 {@code RealisticAlgorithm}과 공유하지 않고 이 클래스에 따로 둔 이유는, 두 카드가 같은
 * 값을 써야 할 이유가 없기 때문이다. 한쪽 기본값을 바꿀 때 다른 쪽이 따라 움직이면 곤란하다.
 *
 * <h3>지역을 시도로 받으면 시도 전체를 한 통에 넣는다</h3>
 * 시군구를 고르지 않는다. 시도의 모든 시군구 실거래를 섞어 정렬한 중앙값 하나를 낸다. 그래서 응답의
 * 지역은 "서울특별시"이지 "중랑구"가 아니다. 사용자가 시군구를 묻지 않았는데 우리가 정해 줄 이유가
 * 없기 때문이며, 감당 가능한 동네를 짚어 주는 일은 {@code REALISTIC}의 몫이다.
 *
 * <p>다만 시도 중앙값은 비싼 지역이 끌어올린 값이라 실제로 갈 수 있는 동네와 동떨어질 수 있다.
 * 그것을 감추지 않는 것이 이 카드의 역할이고, 옆에 나란히 놓이는 REALISTIC 카드가 균형을 잡는다.
 *
 * <h3>표본이 적어도 버리지 않는다</h3>
 * {@code RealisticAlgorithm}은 표본이 적은 조합을 버린다. 중앙값으로 후보들을 <b>비교해 하나를 고르는</b>
 * 것이 일이라, 한두 건짜리 값이 선택을 왜곡하기 때문이다. 이 카드는 고르는 것이 아니라 <b>그대로
 * 보고하는</b> 것이 일이므로 버리지 않고, 대신 실거래 건수를 응답에 함께 실어 화면이 판단하게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceAlgorithm implements RecommendationAlgorithm {

    /**
     * 목표 시점 미지정 시 기본값(개월).
     *
     * <p>주택임대차보호법상 기본 임대차 기간이 2년이라, 사용자가 정하지 않았을 때 다음 계약 주기를
     * 목표로 잡는 것이 자연스럽다.
     */
    private static final int DEFAULT_TARGET_MONTHS = 24;

    /** 주거유형 미지정 시 기본값. 실거래 표본이 가장 많아 데이터가 비는 조건이 될 확률이 낮다. */
    private static final HousingType DEFAULT_HOUSING_TYPE = HousingType.APT;

    /** 거래유형 미지정 시 기본값 */
    private static final DealType DEFAULT_DEAL_TYPE = DealType.JEONSE;

    /** 평수 미지정 시 기본 구간(평) */
    private static final int DEFAULT_AREA_MIN = 15;
    private static final int DEFAULT_AREA_MAX = 19;

    /** 보증금 필터 미지정 시 사용할 상한(원). 사실상 무제한. */
    private static final long DEPOSIT_MAX_DEFAULT = 100_000_000_000L;

    private final RentMedianService rentMedianService;
    private final LoanPlanCalculator loanPlanCalculator;
    private final BudgetCalculator budgetCalculator;
    private final MonteCarloService monteCarloService;

    @Override
    public List<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request, MemberFinancialContext ctx) {

        YearMonth targetDate = request.getTargetDate() != null
                ? request.getTargetDate()
                : YearMonth.now().plusMonths(DEFAULT_TARGET_MONTHS);
        long targetMonths = RecommendationAlgorithm.monthsUntil(targetDate);

        AssetNetWorthBreakdown netWorth = ctx.netWorth();
        long effectiveSaving  = ctx.effectiveSaving();

        HousingType housingType = request.getPropertyType() != null
                ? request.getPropertyType() : DEFAULT_HOUSING_TYPE;
        DealType dealType = request.getTradeType() != null
                ? request.getTradeType() : DEFAULT_DEAL_TYPE;
        int areaMin = request.getSizeMin() != null ? request.getSizeMin() : DEFAULT_AREA_MIN;
        int areaMax = request.getSizeMax() != null ? request.getSizeMax() : DEFAULT_AREA_MAX;

        RentMedianResponse median;
        try {
            median = rentMedianService.getMedian(
                    buildMedianRequest(request, housingType, dealType, areaMin, areaMax));
        } catch (RuntimeException e) {
            log.warn("실거래 조회에 실패해 선호 조건을 추천하지 못했습니다. memberId={}, regionCode={}",
                    memberId, request.getRegionCode(), e);
            return emptyCards();
        }

        // 표본이 적은 것은 그대로 두지만, 한 건도 없으면 보여줄 금액 자체가 없다.
        // 카드를 빼지 않고 condition=null로 내보내, 프론트가 4슬롯(두 PREFERENCE·REALISTIC·HOLD_OUT)을
        // 항상 같은 자리에 그리도록 한다.
        if (median.getSampleCount() == 0 || median.getDeposit().getMedian() == null) {
            log.info("선호 조건에 해당하는 실거래가 없습니다. memberId={}, regionCode={}",
                    memberId, request.getRegionCode());
            return emptyCards();
        }

        long deposit = median.getDeposit().getMedian();
        long monthlyRent = dealType == DealType.WOLSE && median.getMonthlyRent().getMedian() != null
                ? median.getMonthlyRent().getMedian() : 0L;

        // MC로 목표 시점의 예상 보증금을 투영한다.
        // PREFERENCE는 조건이 고정이므로 REALISTIC과 동일하게 수렴 루프 없이 1회 시뮬레이션으로 끝낸다.
        // MC 실패 시 현재 시세를 그대로 사용한다.
        long projectedDeposit = deposit;
        try {
            PriceModelRequest priceReq = RecommendationAlgorithm.buildPriceModelRequest(
                    median.getRegionCode(), housingType, dealType, areaMin, areaMax);
            long budgetAtT = budgetCalculator.calculate(netWorth, effectiveSaving, targetMonths);
            MonteCarloEngine.Result mc = monteCarloService.simulate(
                    priceReq, deposit, budgetAtT, (int) targetMonths);
            projectedDeposit = mc.priceP50();
        } catch (RuntimeException e) {
            log.debug("MC 실패, 현재 시세 폴백. memberId={}, regionCode={}",
                    memberId, request.getRegionCode(), e);
        }

        // 두 카드가 공유하는 조건. 같은 시세·같은 조건을 계산 방향만 달리해 보여준다.
        GoalRecommendationResponse.Condition condition = GoalRecommendationResponse.Condition.builder()
                .regionCode(median.getRegionCode())
                .regionName(median.getRegionName())
                .housingType(housingType)
                .dealType(dealType)
                .areaMin(areaMin)
                .areaMax(areaMax)
                .depositMin(request.getDepositMin() != null ? request.getDepositMin() : 0L)
                .depositMax(request.getDepositMax() != null ? request.getDepositMax() : DEPOSIT_MAX_DEFAULT)
                .monthlyRent(monthlyRent)
                .sampleCount(median.getSampleCount())
                .marketMedianAmount(projectedDeposit)
                .build();

        // ① 저축 고정 — 사용자의 월 저축액을 그대로 두고 도달 시점을 계산
        LoanPlans savingFixedPlans = loanPlanCalculator.calculateSavingFixed(
                memberId, projectedDeposit, netWorth, effectiveSaving);
        GoalRecommendationResponse.RecommendationItem savingFixedCard =
                GoalRecommendationResponse.RecommendationItem.builder()
                        .type(AlgorithmType.PREFERENCE_SAVING_FIXED)
                        .condition(condition)
                        .loanX(savingFixedPlans.getLoanX())
                        .loanO(savingFixedPlans.getLoanO())
                        .build();

        // ② 시점 고정 — 목표 시점을 고정하고 필요한 월 저축액을 역산.
        // 목표 시점을 입력하지 않았으면 고정할 시점이 없어 조건 없는(null) 카드를 낸다.
        GoalRecommendationResponse.RecommendationItem dateFixedCard;
        if (request.getTargetDate() != null) {
            LoanPlans dateFixedPlans = loanPlanCalculator.calculate(memberId, projectedDeposit, targetDate, effectiveSaving);
            GoalRecommendationResponse.LoanOPlan dateFixedLoanO = dateFixedPlans.getLoanO();
            if (dateFixedLoanO != null) {
                // 시점을 고정한 카드라 대출은 개월을 줄이는 게 아니라 필요 저축액을 낮춘다 → 단축 개월은 0
                dateFixedLoanO = dateFixedLoanO.toBuilder().shortenedMonths(0L).build();
            }
            dateFixedCard = GoalRecommendationResponse.RecommendationItem.builder()
                    .type(AlgorithmType.PREFERENCE_DATE_FIXED)
                    .condition(condition)
                    .loanX(dateFixedPlans.getLoanX())
                    .loanO(dateFixedLoanO)
                    .build();
        } else {
            dateFixedCard = GoalRecommendationResponse.RecommendationItem.builder()
                    .type(AlgorithmType.PREFERENCE_DATE_FIXED)
                    .build();
        }

        return List.of(savingFixedCard, dateFixedCard);
    }

    /**
     * 데이터가 없을 때도 카드를 빼지 않고 두 장 모두 condition=null로 내보낸다.
     * 프론트가 4슬롯(PREFERENCE 2 · REALISTIC · HOLD_OUT)을 항상 같은 자리에 그리도록 하기 위함이다.
     */
    private java.util.List<GoalRecommendationResponse.RecommendationItem> emptyCards() {
        return java.util.List.of(
                GoalRecommendationResponse.RecommendationItem.builder()
                        .type(AlgorithmType.PREFERENCE_SAVING_FIXED).build(),
                GoalRecommendationResponse.RecommendationItem.builder()
                        .type(AlgorithmType.PREFERENCE_DATE_FIXED).build());
    }

    private RentMedianRequest buildMedianRequest(
            GoalRecommendationRequest request, HousingType housingType, DealType dealType,
            int areaMin, int areaMax) {

        RentMedianRequest median = new RentMedianRequest();
        median.setRegionCode(request.getRegionCode());
        median.setHousingType(housingType);
        median.setDealType(dealType);
        median.setAreaMin(areaMin);
        median.setAreaMax(areaMax);
        median.setDepositMin(request.getDepositMin() != null ? request.getDepositMin() : 0L);
        median.setDepositMax(request.getDepositMax() != null ? request.getDepositMax() : DEPOSIT_MAX_DEFAULT);
        median.setMonthlyRentMin(request.getMonthlyRentMin());
        median.setMonthlyRentMax(request.getMonthlyRentMax());
        return median;
    }

}
