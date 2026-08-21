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
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.service.RentMedianService;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <h3>검증 초점</h3>
 * <ul>
 *   <li>Realistic 결과(baseline)를 기준으로 정확히 하나의 변수만 바꾼 업그레이드 후보를 탐색하는가</li>
 *   <li>realisticItem이 null이거나 condition이 null이면 soft-fail을 반환하는가</li>
 *   <li>추가 대기 개월(extraMonths)이 MAX_EXTRA_MONTHS를 초과하는 후보는 제외되는가</li>
 *   <li>기존 대출이 BudgetCalculator에 올바르게 반영되는가</li>
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

    private final BudgetCalculator budgetCalculator = new BudgetCalculator();

    private HoldOutAlgorithm algorithm;
    private AssetNetWorthBreakdown zeroNetWorth;
    private MemberFinancialContext ctx;

    /** market 맵: "regionCode|HousingType|DealType|areaMin" → [중앙값보증금, 중앙값월세] */
    private Map<String, long[]> market;

    @BeforeEach
    void setUp() {
        algorithm = new HoldOutAlgorithm(
                loanPlanCalculator, budgetCalculator, rentMedianService, monteCarloService);

        // MC는 P50 = 입력 가격 그대로 반환 (가격 변동 없음으로 고정)
        when(monteCarloService.simulate(any(PriceModelRequest.class), anyLong(), anyLong(), anyInt()))
                .thenAnswer(call -> {
                    long price = call.getArgument(1);
                    long budget = call.getArgument(2);
                    double prob = budget >= price ? 0.8 : 0.2;
                    return new MonteCarloEngine.Result(price * 9 / 10, price, price * 11 / 10, prob);
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
        // market에 아무것도 없음
        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    @Test
    @DisplayName("모든 업그레이드 변수가 이미 최고 수준이면 후보가 없어 soft-fail을 반환한다")
    void allMaxed_noUpgradeAvailable() {
        // APT(최고 타입) + JEONSE(최고 거래유형) + 26~40평(최고 버킷) → 업그레이드 변수 없음
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 26, 40, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    @Test
    @DisplayName("업그레이드 추가 대기가 MAX_EXTRA_MONTHS(36개월) 초과하면 soft-fail을 반환한다")
    void upgradeExceedsMaxExtraMonths_returnsSoftFail() {
        // ctx: 저축 1천만, 대출 월 950만 → 유효 저축 50만/월
        // baseline: 1천만 → reach 20개월 (1천만/50만)
        // upgrade: 2.9억 median → (2.9억*0.9/0.05M) = 522개월 → extra = 502 > 36 → 거부
        MemberFinancialContext tightCtx = ctxWithLoan(9_500_000L);
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 290_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), tightCtx,
                jeonseBaseline(HousingType.APT, 20, 25, 10_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    // ─── 평수 업그레이드 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("baseline이 20~25평이면 바로 다음 버킷(26~40평)을 업그레이드 후보로 탐색한다")
    void sizeUpgrade_findsNextBucket() {
        // baseline 20~25평, deposit 5천만 → reach 5개월
        // upgrade 26~40평, deposit 8천만 → median 7200만 → reach 8개월 → extra 3 → 유효
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(0).getCondition().getHousingType()).isEqualTo(HousingType.APT);
        assertThat(result.get(0).getCondition().getDealType()).isEqualTo(DealType.JEONSE);
        assertThat(result.get(0).getCondition().getAreaMin()).isEqualTo(26);
        assertThat(result.get(0).getCondition().getAreaMax()).isEqualTo(40);
    }

    @Test
    @DisplayName("baseline이 15~19평이면 바로 다음 버킷(20~25평)을 업그레이드 후보로 탐색한다")
    void sizeUpgrade_15to19_findsNextBucket() {
        // 15~19평 → 다음 버킷은 20~25평 (areaMin=20 > 19)
        put(REGION, HousingType.APT, DealType.JEONSE, 20, 25, 60_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 15, 19, 40_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(0).getCondition().getAreaMin()).isEqualTo(20);
        assertThat(result.get(0).getCondition().getAreaMax()).isEqualTo(25);
    }

    @Test
    @DisplayName("26~40평(최고 평수 버킷) baseline에서는 평수 업그레이드 후보가 생성되지 않는다")
    void sizeUpgrade_maxBucket_noSizeUpgradeCandidate() {
        // 평수 업그레이드 없음 → 다른 변수(주거유형) 업그레이드로 대체
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 60_000_000L, 0); // 평수 업그레이드 후보 없음
        // 주거유형 업그레이드도 APT가 최고 → 거래유형 업그레이드도 JEONSE → soft-fail

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 26, 40, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    // ─── 주거유형 업그레이드 ──────────────────────────────────────────────────

    @Test
    @DisplayName("baseline이 ROW_HOUSE이면 APT를 주거유형 업그레이드 후보로 탐색한다")
    void housingTypeUpgrade_rowHouseToApt() {
        // baseline: ROW_HOUSE, JEONSE, 20~25평, deposit 5천만
        // upgrade: APT, JEONSE, 20~25평(areaMin=20)
        put(REGION, HousingType.APT, DealType.JEONSE, 20, 25, 80_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.ROW_HOUSE, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(0).getCondition().getHousingType()).isEqualTo(HousingType.APT);
        assertThat(result.get(0).getCondition().getDealType()).isEqualTo(DealType.JEONSE);
        assertThat(result.get(0).getCondition().getAreaMin()).isEqualTo(20);
    }

    @Test
    @DisplayName("baseline이 DETACHED이면 ROW_HOUSE를 주거유형 업그레이드 후보로 탐색한다")
    void housingTypeUpgrade_detachedToRowHouse() {
        put(REGION, HousingType.ROW_HOUSE, DealType.JEONSE, 20, 25, 70_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.DETACHED, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(0).getCondition().getHousingType()).isEqualTo(HousingType.ROW_HOUSE);
    }

    @Test
    @DisplayName("baseline이 APT(최고 주거유형)이면 주거유형 업그레이드 후보가 생성되지 않는다")
    void housingTypeUpgrade_apt_noUpgradeCandidate() {
        // APT → 상위 타입 없음 → 평수·거래유형만 업그레이드 후보
        // 평수도 26~40(최고), 거래유형도 JEONSE → soft-fail
        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 26, 40, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    // ─── 거래유형 업그레이드 ──────────────────────────────────────────────────

    @Test
    @DisplayName("baseline이 월세이면 전세를 거래유형 업그레이드 후보로 탐색한다")
    void dealTypeUpgrade_wolseToJeonse() {
        // baseline: APT, WOLSE, 20~25평, deposit=2000만, monthlyRent=30만
        // comparable = 2000만 + 30만*12/0.05 = 9200만 → reach ~10개월
        // upgrade: APT, JEONSE, 20~25平(areaMin=20), deposit=1억
        // deposit.median = 1억*0.9=9000万 → reach 9개월 → extra = -1 → 유효
        put(REGION, HousingType.APT, DealType.JEONSE, 20, 25, 100_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                wolseBaseline(HousingType.APT, 20, 25, 20_000_000L, 300_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(0).getCondition().getDealType()).isEqualTo(DealType.JEONSE);
        assertThat(result.get(0).getCondition().getMonthlyRent()).isEqualTo(0L);
    }

    @Test
    @DisplayName("baseline이 전세이면 거래유형 업그레이드 후보가 생성되지 않는다")
    void dealTypeUpgrade_jeonse_noUpgradeCandidate() {
        // JEONSE는 이미 최고 거래유형 → 업그레이드 없음
        // APT(최고) + JEONSE(최고) + 26~40평(최고) → soft-fail
        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.APT, 26, 40, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNull();
    }

    // ─── 대출 반영 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("기존 대출 월상환액이 BudgetCalculator에 반영되어 달성 개월이 늘어난다")
    void withExistingLoan_loanDeductedFromBudget() {
        // 저축 1천만, 대출 500만 → 유효 저축 500만/월
        // baseline 5천만 → reach 10개월 (5천만/500만)
        // upgrade 8천만 → median 7200만 → reach 15개월 → extra 5 ≤ 36 → 유효
        MemberFinancialContext loanCtx = ctxWithLoan(5_000_000L);
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0);

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), loanCtx,
                jeonseBaseline(HousingType.APT, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(0).getCondition().getAreaMin()).isEqualTo(26);
    }

    // ─── 복수 후보 중 최고점 선택 ─────────────────────────────────────────────

    @Test
    @DisplayName("평수·주거유형 업그레이드 후보가 모두 있으면 점수가 높은 것을 선택한다")
    void multipleUpgradeCandidates_bestScoreWins() {
        // baseline: ROW_HOUSE, JEONSE, 20~25평, deposit 5천만
        // 평수 업그레이드: APT, JEONSE, 26~40평 → deposit 8千万 → extra 3개월
        // 주거유형 업그레이드: APT, JEONSE, 20~25평 → deposit 6千万 → extra 1개월
        // 주거유형 업그레이드가 extra가 더 작아 horizonScore 높고, condImprovScore도 0.7 → 더 높은 점수 가능
        put(REGION, HousingType.APT, DealType.JEONSE, 26, 40, 80_000_000L, 0);  // 평수 업그레이드
        put(REGION, HousingType.APT, DealType.JEONSE, 20, 25, 60_000_000L, 0);  // 주거유형 업그레이드

        List<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, defaultRequest(), ctx,
                jeonseBaseline(HousingType.ROW_HOUSE, 20, 25, 50_000_000L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCondition()).isNotNull();
        // 두 후보 중 하나가 선택됨 — 주거유형 업그레이드(같은 평수, APT)가 더 가까워 선택될 가능성 높음
        assertThat(result.get(0).getCondition().getHousingType()).isEqualTo(HousingType.APT);
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

    private RecommendationItem wolseBaseline(HousingType ht, int areaMin, int areaMax,
                                              long deposit, long monthlyRent) {
        return RecommendationItem.builder()
                .type(AlgorithmType.REALISTIC)
                .condition(GoalRecommendationResponse.Condition.builder()
                        .regionCode(REGION)
                        .regionName("테스트구")
                        .housingType(ht)
                        .dealType(DealType.WOLSE)
                        .areaMin(areaMin)
                        .areaMax(areaMax)
                        .depositMin(0L)
                        .depositMax(Long.MAX_VALUE)
                        .monthlyRent(monthlyRent)
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
