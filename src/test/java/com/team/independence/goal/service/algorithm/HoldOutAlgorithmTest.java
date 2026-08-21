package com.team.independence.goal.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.GoalRecommendationResponse.RecommendationItem;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.service.MonteCarloEngine;
import com.team.independence.goal.service.MonteCarloService;
import com.team.independence.goal.service.RecommendationAlgorithm.MemberFinancialContext;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.goal.service.calculator.LoanSchedule;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.PriceModelKey;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.service.PriceModelService;
import com.team.independence.property.service.RentMedianService;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * HoldOut 추천 알고리즘 단위 테스트 (40개 조합 next-rung 방식).
 *
 * <p>BudgetCalculator는 실 구현체를 사용하고, 나머지 외부 의존은 목으로 대체한다.
 * MonteCarlo 목은 P50 = 입력 가격 그대로 반환해(가격 변동 0) 후보 비교를 median 보증금으로 단순화한다.
 *
 * <h3>검증 초점</h3>
 * <ul>
 *   <li>realisticItem/condition이 없으면 soft-fail을 반환하는가</li>
 *   <li>주거유형 × 거래유형 × 평수 버킷 40개 조합 중 baseline보다 비싼 것 중 가장 싼 것을 고르는가
 *       (변수 하나만 바꾼 후보가 아니라 조합 전체에서)</li>
 *   <li>baseline이 이미 최고가 조합이면(위 칸 없음) soft-fail을 반환하는가 — baseline 복제하지 않음</li>
 *   <li>도달 개월 상한이 없어, 아무리 오래 걸려도 후보를 그대로 채택하는가</li>
 *   <li>기존 대출(LoanSchedule)이 도달 계획에 반영되는가</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldOutAlgorithmTest {

    private static final long MEMBER_ID = 1L;
    private static final String REGION = "11110";
    private static final YearMonth NOW = YearMonth.now();

    @Mock private LoanPlanCalculator loanPlanCalculator;
    @Mock private RentMedianService rentMedianService;
    @Mock private MonteCarloService monteCarloService;
    @Mock private PriceModelService priceModelService;

    private final BudgetCalculator budgetCalculator = new BudgetCalculator();

    private HoldOutAlgorithm algorithm;
    private AssetNetWorthBreakdown zeroNetWorth;
    private MemberFinancialContext ctx;

    /** market 맵: "regionCode|HousingType|DealType|areaMin" → [중앙값보증금, 중앙값월세] */
    private Map<String, long[]> market;

    @BeforeEach
    void setUp() {
        algorithm = new HoldOutAlgorithm(
                loanPlanCalculator, budgetCalculator, rentMedianService, monteCarloService, priceModelService);

        // MC는 P50 = 입력 가격 그대로 반환 (가격 변동 없음으로 고정)
        when(monteCarloService.simulate(any(PriceModelResponse.class), anyLong(), anyLong(), anyInt()))
                .thenAnswer(call -> {
                    long price = call.getArgument(1);
                    long budget = call.getArgument(2);
                    double prob = budget >= price ? 0.8 : 0.2;
                    return new MonteCarloEngine.Result(price * 9 / 10, price, price * 11 / 10, prob);
                });

        // 배치 PriceModel: 모든 조합에 대해 임의의 PriceModelResponse를 반환 (MC 스텁이 이 값을 실제로 쓰지 않음)
        when(priceModelService.estimateBatch(anyString(), any()))
                .thenAnswer(call -> {
                    Iterable<PriceModelKey> keys = call.getArgument(1);
                    Map<PriceModelKey, PriceModelResponse> result = new HashMap<>();
                    for (PriceModelKey k : keys) {
                        result.put(k, PriceModelResponse.builder()
                                .regionCode(call.getArgument(0))
                                .housingType(k.housingType())
                                .dealType(k.dealType())
                                .annualDrift(0.0).cagr(0.0).annualVol(0.0)
                                .months(12).build());
                    }
                    return result;
                });

        market = new HashMap<>();

        zeroNetWorth = AssetNetWorthBreakdown.builder()
                .interestBearingAssets(0L)
                .flatRecognizedAssets(0L)
                .build();
        ctx = new MemberFinancialContext(zeroNetWorth, 10_000_000L, List.of());

        when(loanPlanCalculator.calculateSavingFixed(
                anyLong(), anyLong(), any(AssetNetWorthBreakdown.class), anyLong(), any()))
                .thenReturn(LoanPlans.builder().build());
        when(rentMedianService.getBulkMedian(anyString(), anyString(), anyString()))
                .thenAnswer(call -> toBulkMap(call.getArgument(0)));
    }

    // ─── soft-fail 케이스 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("realisticItem이 null이면 soft-fail 카드를 반환한다")
    void nullRealisticItem_returnsSoftFail() {
        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, defaultRequest(), ctx, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AlgorithmType.HOLD_OUT);
        assertThat(result.get(0).getCondition()).isNull();
    }

    @Test
    @DisplayName("Realistic 결과의 condition이 null이면 soft-fail 카드를 반환한다")
    void nullCondition_returnsSoftFail() {
        RecommendationItem noCondition = RecommendationItem.builder()
                .type(AlgorithmType.REALISTIC).build();

        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, defaultRequest(), ctx, noCondition);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    @Test
    @DisplayName("시장 데이터가 없으면 soft-fail 카드를 반환한다")
    void noMarketData_returnsSoftFail() {
        // market에 아무것도 없음 → 40개 조합 전부 median 조회 실패
        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    @Test
    @DisplayName("baseline이 이미 40개 조합 중 최고가면(위 칸 없음) soft-fail을 반환하고 baseline을 복제하지 않는다")
    void baselineIsTopRung_returnsSoftFailWithoutCloning() {
        // baseline 자체가 시장에서 가장 비싼 조합 (다른 조합은 전부 baseline보다 싸다)
        put(REGION, HousingType.APT, DealType.JEONSE, 15, 19, 30_000_000L, 0);
        put(REGION, HousingType.APT, DealType.JEONSE, 20, 25, 50_000_000L, 0); // baseline과 동일 조합

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AlgorithmType.HOLD_OUT);
        assertThat(result.get(0).getCondition()).isNull();
    }

    // ─── next-rung 선택 (40개 조합 전체) ──────────────────────────────────────

    @Test
    @DisplayName("baseline보다 비싼 조합 중 가장 싼 것을 다음 등급으로 고른다")
    void picksCheapestCombinationAboveBaseline() {
        // baseline: APT/JEONSE/20~25평, 5천만
        // 후보 A: APT/JEONSE/26~40평, median 7200만(=8천만*0.9)
        // 후보 B: ROW_HOUSE/JEONSE/26~40평, median 5400만(=6천만*0.9) — 더 싸지만 baseline보다는 비쌈
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0);
        put(REGION, HousingType.ROW_HOUSE, DealType.JEONSE, 26, 40, 60_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        var condition = result.get(0).getCondition();
        assertThat(condition).isNotNull();
        // 두 후보 모두 baseline(5천만)보다 비싸다. 그중 더 싼 ROW_HOUSE(5400만)가 다음 등급으로 선택된다.
        assertThat(condition.getHousingType()).isEqualTo(HousingType.ROW_HOUSE);
        assertThat(condition.getAreaMin()).isEqualTo(26);
        assertThat(condition.getMarketMedianAmount()).isEqualTo(54_000_000L);
    }

    @Test
    @DisplayName("baseline과 같은 조합은 자기 자신보다 비쌀 수 없어 후보에서 자연히 제외된다")
    void baselineCombinationItselfIsExcluded() {
        // baseline과 같은 조합(APT/JEONSE/20~25평)의 시세도 market에 넣어두지만,
        // comparableAmount가 baseline과 같아 '더 비싼 것'에서 제외되고 26~40만 후보가 된다.
        put(REGION, HousingType.APT, DealType.JEONSE, 20, 25, 50_000_000L, 0); // == baseline
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 70_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition().getAreaMin()).isEqualTo(26);
    }

    @Test
    @DisplayName("거래유형이 달라도(월세 환산 포함) 가격순으로 비교해 다음 등급을 고른다")
    void comparesAcrossDealTypesByComparableAmount() {
        // baseline: APT/JEONSE/20~25평, 5천만
        // 월세 후보: APT/WOLSE/20~25평, 보증금 2천만 + 월세 30만 → 환산 2천만+30만*12/0.05=9200만
        // 전세 후보: APT/JEONSE/26~40평, median 9900만(baseline보다 비싸지만 월세 환산보다도 비쌈)
        put(REGION, HousingType.APT, DealType.WOLSE, 20, 25, 20_000_000L, 300_000L);
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 110_000_000L, 0); // median = 9900만

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        var condition = result.get(0).getCondition();
        // 월세 환산액(9200만) < 전세 median(9900만) → 월세 후보가 다음 등급
        // getMedian()은 Quartile.of(*0.8, *0.9, *1.0)의 가운데 값이라 300,000 * 0.9 = 270,000이 된다.
        assertThat(condition.getDealType()).isEqualTo(DealType.WOLSE);
        assertThat(condition.getMonthlyRent()).isEqualTo(270_000L);
    }

    @Test
    @DisplayName("다음 등급이 월세면 loanX·loanO의 monthlySaving에 월세를 더해 보여준다")
    void addsMonthlyRentToLoanXAndLoanOWhenNextRungIsWolse() {
        // baseline: APT/JEONSE/20~25평, 5천만
        // 다음 등급: APT/WOLSE/26~40평, 보증금 5천만 + 월세 30만 → comparable = 5천만+30만*12/0.05=1억1800만 > baseline
        put(REGION, HousingType.APT, DealType.WOLSE, 26, 40, 50_000_000L, 300_000L);

        when(loanPlanCalculator.calculateSavingFixed(
                anyLong(), anyLong(), any(AssetNetWorthBreakdown.class), anyLong(), any()))
                .thenReturn(LoanPlans.builder()
                        .loanX(GoalRecommendationResponse.LoanXPlan.builder()
                                .targetAmount(45_000_000L).targetDate(NOW.plusMonths(10)).monthlySaving(1_000_000L)
                                .build())
                        .loanO(GoalRecommendationResponse.LoanOPlan.builder()
                                .loanAmount(10_000_000L).targetAmount(35_000_000L).targetDate(NOW.plusMonths(7))
                                .monthlySaving(1_000_000L).shortenedMonths(3L)
                                .build())
                        .build());

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        var item = result.get(0);
        assertThat(item.getCondition().getDealType()).isEqualTo(DealType.WOLSE);
        // getMedian() = 30만 * 0.9 = 27만
        assertThat(item.getCondition().getMonthlyRent()).isEqualTo(270_000L);
        // 목의 monthlySaving(100만) + 월세(27만) = 127만
        assertThat(item.getLoanX().getMonthlySaving()).isEqualTo(1_270_000L);
        assertThat(item.getLoanO().getMonthlySaving()).isEqualTo(1_270_000L);
        // 월세와 무관한 필드는 그대로 유지된다
        assertThat(item.getLoanO().getShortenedMonths()).isEqualTo(3L);
    }

    @Test
    @DisplayName("다음 등급이 전세면 monthlySaving을 그대로 둔다")
    void keepsMonthlySavingUnchangedWhenNextRungIsJeonse() {
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0);

        when(loanPlanCalculator.calculateSavingFixed(
                anyLong(), anyLong(), any(AssetNetWorthBreakdown.class), anyLong(), any()))
                .thenReturn(LoanPlans.builder()
                        .loanX(GoalRecommendationResponse.LoanXPlan.builder()
                                .targetAmount(72_000_000L).targetDate(NOW.plusMonths(20)).monthlySaving(1_000_000L)
                                .build())
                        .build());

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLoanX().getMonthlySaving()).isEqualTo(1_000_000L);
    }

    // ─── 도달 개월 상한 없음 ─────────────────────────────────────────────────

    @Test
    @DisplayName("도달까지 아무리 오래 걸려도 개월 상한 없이 다음 등급을 채택한다")
    void noUpperBoundOnExtraMonths() {
        // 저축 여력이 매우 작아 도달까지 수백 개월이 걸려도 후보를 그대로 채택해야 한다.
        MemberFinancialContext tightCtx = ctxWithLoan(9_990_000L); // 유효 저축 1만원
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 290_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), tightCtx,
                jeonseBaseline(HousingType.APT, 20, 25, 10_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(0).getCondition().getAreaMin()).isEqualTo(26);
    }

    // ─── 대출 반영 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("기존 대출(LoanSchedule)이 도달 계획 계산에 그대로 전달된다")
    void existingLoanPassedToLoanPlanCalculator() {
        MemberFinancialContext loanCtx = ctxWithLoan(5_000_000L);
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0);

        algorithm.recommend(
                MEMBER_ID, defaultRequest(), loanCtx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        Mockito.verify(loanPlanCalculator).calculateSavingFixed(
                Mockito.eq(MEMBER_ID), anyLong(), any(AssetNetWorthBreakdown.class),
                Mockito.eq(10_000_000L), Mockito.eq(loanCtx.loanSchedules()));
    }

    @Test
    @DisplayName("다음 등급의 투영 보증금으로 calculateSavingFixed를 호출한다")
    void reachesNextRungBySavingFixed() {
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0); // median = 72,000,000

        algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        ArgumentCaptor<Long> amount = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(loanPlanCalculator).calculateSavingFixed(
                anyLong(), amount.capture(), any(AssetNetWorthBreakdown.class), anyLong(), any());
        assertThat(amount.getValue()).isEqualTo(72_000_000L);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private RecommendationItem jeonseBaseline(HousingType ht, int areaMin, int areaMax, long deposit) {
        return RecommendationItem.builder()
                .type(AlgorithmType.REALISTIC)
                .condition(GoalRecommendationResponse.Condition.builder()
                        .regionCode(REGION)
                        .regionName("테스트구")
                        .housingType(ht)
                        .dealType(DealType.JEONSE)
                        .areaMin(areaMin)
                        .areaMax(areaMax)
                        .depositMin(0L)
                        .depositMax(Long.MAX_VALUE)
                        .monthlyRent(0L)
                        .sampleCount(10)
                        .marketMedianAmount(deposit)
                        .build())
                .build();
    }

    private MemberFinancialContext ctxWithLoan(long monthlyPayment) {
        return new MemberFinancialContext(zeroNetWorth, 10_000_000L,
                List.of(new LoanSchedule(monthlyPayment, 1200L)));
    }

    private GoalRecommendationRequest defaultRequest() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode(REGION);
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setSizeMin(20);
        request.setSizeMax(25);
        request.setTargetDate(NOW.plusMonths(24));
        return request;
    }

    private void put(String regionCode, HousingType ht, DealType dt,
                     int areaMin, int areaMax, long medianDeposit, long medianRent) {
        market.put(regionCode + "|" + ht + "|" + dt + "|" + areaMin,
                new long[]{medianDeposit, medianRent});
    }

    /**
     * market에서 regionCode에 해당하는 항목을 HoldOutAlgorithm이 기대하는 키 형식(ht|dt|areaMin)으로 반환한다.
     * deposit은 Q2를 medianDeposit*0.9로, Q3를 medianDeposit으로 설정해 getMedian()이 medianDeposit*0.9를 반환한다.
     */
    private Map<String, RentMedianResponse> toBulkMap(String regionCode) {
        Map<String, RentMedianResponse> bulk = new HashMap<>();
        for (Map.Entry<String, long[]> entry : market.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            if (!parts[0].equals(regionCode)) continue;
            HousingType ht = HousingType.valueOf(parts[1]);
            DealType dt    = DealType.valueOf(parts[2]);
            int areaMin    = Integer.parseInt(parts[3]);
            long[] vals    = entry.getValue();
            long medDeposit = vals[0];
            long medRent    = vals[1];
            bulk.put(ht + "|" + dt + "|" + areaMin, RentMedianResponse.builder()
                    .regionCode(regionCode)
                    .regionName("테스트구")
                    .housingType(ht)
                    .dealType(dt)
                    .sampleCount(15)
                    .deposit(Quartile.of(medDeposit * 8 / 10, medDeposit * 9 / 10, medDeposit))
                    .monthlyRent(medRent > 0
                            ? Quartile.of(medRent * 8 / 10, medRent * 9 / 10, medRent)
                            : Quartile.empty())
                    .build());
        }
        return bulk;
    }
}
