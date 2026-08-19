package com.team.independence.goal.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.service.RecommendationAlgorithm.MemberFinancialContext;
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
 *
 * <p>검증 초점
 * <ul>
 *   <li>기존 대출 월상환액이 월저축액에서 사전 차감된 채 예산 계산에 전달되는가</li>
 *   <li>사용자가 지정한 areaMax보다 큰 버킷만 업그레이드 후보로 허용하는가</li>
 *   <li>시군구 pool이 비면 상위 시도로 확장하는가</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldOutAlgorithmTest {

    private static final long MEMBER_ID = 1L;
    private static final long 억 = 100_000_000L;
    private static final long 만 = 10_000L;

    private static final YearMonth NOW = YearMonth.now();

    @Mock private LoanPlanCalculator loanPlanCalculator;
    @Mock private GoalMapper goalMapper;
    @Mock private GoalHousingMapper goalHousingMapper;
    @Mock private RentMedianService rentMedianService;
    @Mock private MonteCarloService monteCarloService;

    private final BudgetCalculator budgetCalculator = new BudgetCalculator();

    private HoldOutAlgorithm algorithm;
    private AssetNetWorthBreakdown defaultNetWorth;
    private MemberFinancialContext ctx;

    /** "regionCode|주거유형|거래유형|최소평수" → {Q3 보증금, Q3 월세} */
    private Map<String, long[]> market;

    @BeforeEach
    void setUp() {
        algorithm = new HoldOutAlgorithm(
                loanPlanCalculator, budgetCalculator, goalMapper, goalHousingMapper,
                rentMedianService, monteCarloService);

        when(monteCarloService.simulate(any(PriceModelRequest.class), anyLong(), anyLong(), anyInt()))
                .thenAnswer(call -> {
                    long initialPrice = call.getArgument(1);
                    long budgetAtT = call.getArgument(2);
                    double successProb = budgetAtT >= initialPrice ? 0.8 : 0.2;
                    return new MonteCarloEngine.Result(
                            initialPrice * 9 / 10, initialPrice, initialPrice * 11 / 10, successProb);
                });
        market = new HashMap<>();

        defaultNetWorth = AssetNetWorthBreakdown.builder()
                .interestBearingAssets(0L)
                .flatRecognizedAssets(0L)
                .build();
        ctx = new MemberFinancialContext(defaultNetWorth, 10_000_000L, 0L);
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(null);
        when(loanPlanCalculator.calculate(anyLong(), anyLong(), any()))
                .thenReturn(LoanPlans.builder().build());
        when(rentMedianService.getMedian(any())).thenAnswer(call -> toResponse(call.getArgument(0)));
    }

    // ===== DSR 차감 관련 =====

    @Test
    @DisplayName("대출이 없으면 월저축액 전액으로 예산을 계산해 업그레이드 후보를 추천한다")
    void noLoanUsesFullMonthlySaving() {
        // 월저축 1천만, 대출 없음 → 2억 보증금에 ~19개월 → PATIENCE_BONUS(48) 이내 → 추천
        // 요청: 20~25평 → 업그레이드 버킷인 26~40평(areaMin=26 > areaMax=25)만 허용
        put("11110", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition().getAreaMin()).isEqualTo(26);
    }

    @Test
    @DisplayName("기존 대출 월상환액을 월저축액에서 차감한 뒤 예산을 계산한다")
    void deductsExistingLoanPaymentFromSaving() {
        // 월저축 1천만, 대출 월상환 900만 → baseSaving 100만
        // 2억을 100만/월로 모으면 ~169개월 → PATIENCE_BONUS(48) 초과 → soft-fail
        MemberFinancialContext ctx = ctxWithLoan(9_000_000L);
        put("11110", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition()).isNull();
    }

    @Test
    @DisplayName("월세 후보는 기존 대출 차감 후 임대료도 추가 차감한다")
    void deductsRentOnTopOfLoanPayment() {
        // 월저축 1천만, 대출 월상환 500만 → baseSaving 500만
        // 월세 Q3 = 450만 → effectiveSaving 50만
        // 1억 보증금을 50만/월로 모으면 ~180개월 → PATIENCE_BONUS(48) 초과 → 추천 없음
        MemberFinancialContext ctx = ctxWithLoan(5_000_000L);
        put("11110", HousingType.APT, DealType.WOLSE, 26, 40, 1 * 억, 450 * 만);

        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.WOLSE);
        request.setSizeMin(20);
        request.setSizeMax(25);
        request.setTargetDate(NOW);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request, ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition()).isNull();
    }

    @Test
    @DisplayName("대출 월상환액이 월저축액보다 커도 저축액이 음수가 되지 않는다")
    void clipsNegativeSavingToZero() {
        // 대출 1500만 > 저축 1000만 → baseSaving = 0 → 현재 자산(0원)으로 2억 도달 불가 → soft-fail
        MemberFinancialContext ctx = ctxWithLoan(15_000_000L);
        put("11110", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition()).isNull();
    }

    @Test
    @DisplayName("시장 데이터가 없으면 soft-fail 카드를 반환한다")
    void returnsEmptyWhenNoMarketData() {
        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition()).isNull();
    }

    // ===== 평수 업그레이드 필터 =====

    @Test
    @DisplayName("평수 지정 시 지정 areaMax 이하 버킷은 업그레이드가 아니므로 제외된다")
    void filtersBucketsNotLargerThanSpecifiedMax() {
        // 요청 sizeMax=25 → areaMin <= 25인 버킷(15~19, 20~25) 모두 제외 → soft-fail
        put("11110", HousingType.APT, DealType.JEONSE, 15, 19, 2 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 20, 25, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition()).isNull();
    }

    @Test
    @DisplayName("평수 지정 시 areaMin > base.areaMax 인 버킷만 업그레이드 후보로 허용된다")
    void allowsOnlyBucketsLargerThanSpecifiedMax() {
        // 요청 sizeMax=25 → 26~40(areaMin=26 > 25)만 허용
        put("11110", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition().getAreaMin()).isEqualTo(26);
        assertThat(result.get().getCondition().getAreaMax()).isEqualTo(40);
    }

    @Test
    @DisplayName("평수 미지정이면 SIZE_BUCKETS 전체를 탐색해 가장 적합한 버킷을 선택한다")
    void exploresAllSizeBucketsWhenAreaNotSpecified() {
        // 면적 미지정 → 필터 없이 15~19평 버킷도 탐색
        put("11110", HousingType.APT, DealType.JEONSE, 15, 19, 2 * 억, 0);

        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setTargetDate(NOW);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request, ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition().getAreaMin()).isEqualTo(15);
    }

    // ===== 지역 확장 폴백 =====

    @Test
    @DisplayName("시군구 pool이 비면 상위 시도 코드로 확장해 업그레이드 후보를 탐색한다")
    void expandsToSidoWhenSigunguPoolEmpty() {
        // 11110(종로구)에는 데이터 없고 11(서울)에만 26~40평 데이터 있음
        put("11", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition().getRegionCode()).isEqualTo("11");
    }

    @Test
    @DisplayName("시군구에 업그레이드 후보가 있으면 시도로 확장하지 않는다")
    void doesNotExpandToSidoWhenSigunguHasCandidates() {
        // 11110에 데이터 있음 → 시도 11로 확장 불필요
        put("11110", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);
        put("11",    HousingType.APT, DealType.JEONSE, 26, 40, 1 * 억, 0); // 더 저렴하지만 탐색 안 됨

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition().getRegionCode()).isEqualTo("11110");
    }

    @Test
    @DisplayName("시도 코드(2자리)로 요청하면 지역 확장 없이 해당 시도 내에서만 탐색한다")
    void doesNotExpandWhenAlreadySidoCode() {
        // 시도 코드 "11" 요청 → 확장 없음, 데이터 없으면 empty
        put("11110", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0); // 시군구에 데이터 있어도 무관

        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setSizeMin(20);
        request.setSizeMax(25);
        request.setTargetDate(NOW);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request, ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getCondition()).isNull();
    }

    // ===== 메시지 =====

    @Test
    @DisplayName("평수 지정 + targetDate 없음: reason에 '저축하면'과 평수 범위가 포함된다")
    void reasonIncludesSaveMonthsWhenNoTarget() {
        put("11110", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);

        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setSizeMin(20);
        request.setSizeMax(25);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request, ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getReason()).contains("저축하면").contains("26~40평");
    }

    @Test
    @DisplayName("지역 확장으로 찾은 경우: reason에 '요청하신 지역'과 확장된 지역명이 포함된다")
    void reasonMentionsOriginalRegionWhenExpanded() {
        put("11", HousingType.APT, DealType.JEONSE, 26, 40, 2 * 억, 0);

        Optional<RecommendationItem> result = algorithm.recommend(MEMBER_ID, aptJeonseRequest("11110"), ctx);

        assertThat(result).isPresent();
        assertThat(result.get().getReason()).contains("요청하신 지역");
    }

    // ===== 헬퍼 =====

    private MemberFinancialContext ctxWithLoan(long loanPayment) {
        return new MemberFinancialContext(defaultNetWorth, 10_000_000L, loanPayment);
    }

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
