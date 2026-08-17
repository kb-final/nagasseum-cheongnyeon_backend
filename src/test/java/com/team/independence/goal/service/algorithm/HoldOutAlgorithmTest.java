package com.team.independence.goal.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.summary.AssetSummaryResponse;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse.RecommendationItem;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.mapper.GoalMapper;
import com.team.independence.goal.service.MonteCarloEngine;
import com.team.independence.goal.service.MonteCarloService;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.service.RentMedianService;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * HoldOut 추천 알고리즘 단위 테스트.
 *
 * <p>BudgetCalculator는 실 구현체를 사용하고, 나머지 외부 의존은 목으로 대체한다.
 * 검증 초점은 "기존 대출 월상환액이 월저축액에서 사전 차감된 채 예산 계산에 전달되는가"이며,
 * 월세 후보에서 임대료가 추가로 차감되는 동작도 함께 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldOutAlgorithmTest {

    private static final long MEMBER_ID = 1L;
    private static final long 억 = 100_000_000L;
    private static final long 만 = 10_000L;

    /**
     * targetDate = NOW → n = 0, maxExtra = 24.
     * 이 설정에서 totalMonths <= 24이면 후보가 통과한다.
     */
    private static final YearMonth NOW = YearMonth.now();

    @Mock private LoanPlanCalculator loanPlanCalculator;
    @Mock private GoalMapper goalMapper;
    @Mock private GoalHousingMapper goalHousingMapper;
    @Mock private AssetSummaryService assetSummaryService;
    @Mock private RentMedianService rentMedianService;
    @Mock private MonteCarloService monteCarloService;

    // 순수 계산 클래스는 실 구현체를 사용한다.
    private final BudgetCalculator budgetCalculator = new BudgetCalculator();

    private HoldOutAlgorithm algorithm;

    /** "regionCode|주거유형|거래유형|최소평수" → {Q3 보증금, Q3 월세} */
    private Map<String, long[]> market;

    @BeforeEach
    void setUp() {
        algorithm = new HoldOutAlgorithm(
                loanPlanCalculator, budgetCalculator, goalMapper, goalHousingMapper,
                assetSummaryService, rentMedianService, monteCarloService);

        // 기본 스텁: priceP50 = initialPrice (가격 변동 없음), 예산 >= 가격이면 성공
        when(monteCarloService.simulate(any(PriceModelRequest.class), anyLong(), anyLong(), anyInt()))
                .thenAnswer(call -> {
                    long initialPrice = call.getArgument(1);
                    long budgetAtT = call.getArgument(2);
                    double successProb = budgetAtT >= initialPrice ? 0.8 : 0.2;
                    return new MonteCarloEngine.Result(
                            initialPrice * 9 / 10, initialPrice, initialPrice * 11 / 10, successProb);
                });
        market = new HashMap<>();

        // 활성 목표 없음 → request 조건을 그대로 사용
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(null);
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(
                AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(0L)
                        .build());
        when(assetSummaryService.getSummary(MEMBER_ID)).thenReturn(
                AssetSummaryResponse.builder().monthlySavings(10_000_000L).build());
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(0L);
        when(loanPlanCalculator.calculate(anyLong(), anyLong(), any()))
                .thenReturn(LoanPlans.builder().build());
        when(rentMedianService.getMedian(any())).thenAnswer(call -> toResponse(call.getArgument(0)));
    }

    @Test
    @DisplayName("대출이 없으면 월저축액 전액으로 예산을 계산해 후보를 추천한다")
    void noLoanUsesFullMonthlySaving() {
        // 월저축 1천만, 대출 없음 → 2억 보증금에 ~19개월 → maxExtra(24) 이내 → 추천
        put("11110", HousingType.APT, DealType.JEONSE, 20, 25, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"));

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("기존 대출 월상환액을 월저축액에서 차감한 뒤 예산을 계산한다")
    void deductsExistingLoanPaymentFromSaving() {
        // 월저축 1천만, 대출 월상환 900만 → baseSaving 100만
        // 2억을 100만/월로 모으면 ~169개월 → maxExtra(24) 초과 → 추천 없음
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(9_000_000L);
        put("11110", HousingType.APT, DealType.JEONSE, 20, 25, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("월세 후보는 기존 대출 차감 후 임대료도 추가 차감한다")
    void deductsRentOnTopOfLoanPayment() {
        // 월저축 1천만, 대출 월상환 500만 → baseSaving 500만
        // 월세 Q3 = 450만 → effectiveSaving 50만
        // 1억 보증금을 50만/월로 모으면 ~180개월 → maxExtra(24) 초과 → 추천 없음
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(5_000_000L);
        put("11110", HousingType.APT, DealType.WOLSE, 20, 25, 1 * 억, 450 * 만);

        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.WOLSE);
        request.setSizeMin(20);
        request.setSizeMax(25);
        request.setTargetDate(NOW);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("대출 월상환액이 월저축액보다 커도 저축액이 음수가 되지 않는다")
    void clipsNegativeSavingToZero() {
        // 대출 1500만 > 저축 1000만 → baseSaving = 0 → 현재 자산(0원)으로 2억 도달 불가
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(15_000_000L);
        put("11110", HousingType.APT, DealType.JEONSE, 20, 25, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("시장 데이터가 없으면 추천하지 않는다")
    void returnsEmptyWhenNoMarketData() {
        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("면적을 지정하지 않으면 SIZE_BUCKETS 전체를 탐색한다")
    void exploresAllSizeBucketsWhenAreaNotSpecified() {
        // {15,19} 버킷에만 데이터 — 면적 미지정이면 이 버킷도 탐색되어 추천이 돌아와야 한다
        put("11110", HousingType.APT, DealType.JEONSE, 15, 19, 2 * 억, 0);

        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setTargetDate(NOW);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition().getAreaMin()).isEqualTo(15);
    }

    @Test
    @DisplayName("면적 지정 시 지정 버킷보다 작은 SIZE_BUCKETS는 다운그레이드로 제외된다")
    void filtersSmallerSizeBucketsThanSpecified() {
        // 요청 면적 20~25 → baseMidArea=22.5 → {15,19} midArea=17 < 22.5 → 제외
        // {20,25}에는 데이터 없음 → 최종 추천 없음
        put("11110", HousingType.APT, DealType.JEONSE, 15, 19, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"));

        assertThat(result).isEmpty();
    }

    // ===== 헬퍼 =====

    private GoalRecommendationRequest aptJeonseRequest(String regionCode) {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode(regionCode);
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setSizeMin(20);
        request.setSizeMax(25);
        request.setTargetDate(NOW);
        return request;
    }

    private void put(String regionCode, HousingType housingType, DealType dealType,
            int areaMin, int areaMax, long q3Deposit, long q3MonthlyRent) {
        market.put(key(regionCode, housingType, dealType, areaMin), new long[]{q3Deposit, q3MonthlyRent});
    }

    private String key(String regionCode, HousingType housingType, DealType dealType, int areaMin) {
        return regionCode + "|" + housingType + "|" + dealType + "|" + areaMin;
    }

    /**
     * market에 등록된 조합은 표본 15건으로 응답한다.
     * HoldOut은 Q3(75분위)를 사용하므로 Q3를 등록한 값으로 설정한다.
     * 미등록 조합은 표본 0건(MIN_SAMPLE_COUNT 미달)으로 응답해 자연스럽게 필터된다.
     */
    private RentMedianResponse toResponse(RentMedianRequest request) {
        long[] found = market.get(key(request.getRegionCode(), request.getHousingType(),
                request.getDealType(), request.getAreaMin()));

        RentMedianResponse.RentMedianResponseBuilder builder = RentMedianResponse.builder()
                .regionCode(request.getRegionCode())
                .regionName("테스트구")
                .housingType(request.getHousingType())
                .dealType(request.getDealType());

        if (found == null) {
            return builder.sampleCount(0).deposit(Quartile.empty()).monthlyRent(Quartile.empty()).build();
        }
        long q3Deposit = found[0];
        long q3MonthlyRent = found[1];
        return builder
                .sampleCount(15)
                .deposit(Quartile.of(q3Deposit * 8 / 10, q3Deposit * 9 / 10, q3Deposit))
                .monthlyRent(q3MonthlyRent > 0
                        ? Quartile.of(q3MonthlyRent * 8 / 10, q3MonthlyRent * 9 / 10, q3MonthlyRent)
                        : Quartile.empty())
                .build();
    }
}
