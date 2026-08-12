package com.team.independence.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.summary.AssetSummaryResponse;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse.RecommendationItem;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.service.RentMedianService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * 현실 우선 추천 알고리즘 테스트.
 *
 * <p>실거래 시세를 조합별로 마음대로 정해 놓고, 알고리즘이 그중 무엇을 고르는지만 본다.
 * 저축 복리 계산({@code calculateMonthToReach})은 "목표액 ÷ 월저축액" 이라는 단순한 식으로 대체했다.
 * 복리를 그대로 쓰면 기대값을 손으로 계산하기 어려워 정작 검증하려는 선택 규칙이 가려지기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealisticAlgorithmTest {

    private static final long MEMBER_ID = 1L;

    /** 월 저축액. 기본 목표 24개월과 곱하면 예산이 2.4억이 된다. */
    private static final long MONTHLY_SAVING = 10_000_000L;

    private static final long 억 = 100_000_000L;
    private static final long 만 = 10_000L;

    @Mock
    private AssetSummaryService assetSummaryService;
    @Mock
    private RentMedianService rentMedianService;
    @Mock
    private RegionMapper regionMapper;
    @Mock
    private LoanPlanCalculator loanPlanCalculator;
    @Mock
    private GoalService goalService;

    private RealisticAlgorithm algorithm;

    /** "지역|주거유형|거래유형|최소평수" → {보증금, 월세, 표본수} */
    private Map<String, long[]> market;

    /** 실거래 조회가 실제로 일어난 조합. 중복 조회를 잡기 위해 순서대로 쌓는다. */
    private List<String> lookedUp;

    @BeforeEach
    void setUp() {
        algorithm = new RealisticAlgorithm(
                assetSummaryService, rentMedianService, regionMapper, loanPlanCalculator, goalService);
        market = new HashMap<>();
        lookedUp = new ArrayList<>();

        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(
                AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(0L)
                        .build());
        when(assetSummaryService.getSummary(MEMBER_ID)).thenReturn(
                AssetSummaryResponse.builder().monthlySavings(MONTHLY_SAVING).build());

        // 목표액 ÷ 월저축액. 저축이 없으면 도달 불가(null).
        when(goalService.calculateMonthToReach(any(), anyLong(), anyLong())).thenAnswer(call -> {
            long monthlySaving = call.getArgument(1);
            long targetAmount = call.getArgument(2);
            if (monthlySaving <= 0) {
                return null;
            }
            return (long) Math.ceil((double) targetAmount / monthlySaving);
        });

        when(rentMedianService.getMedian(any())).thenAnswer(call -> {
            RentMedianRequest asked = call.getArgument(0);
            lookedUp.add(key(asked.getRegionCode(), asked.getHousingType(),
                    asked.getDealType(), asked.getAreaMin()));
            return toResponse(asked);
        });

        when(loanPlanCalculator.calculate(anyLong(), anyLong(), any()))
                .thenReturn(LoanPlans.builder().build());
    }

    @Test
    @DisplayName("목표 시점 안에 가능한 후보 중 가장 좋은(비싼) 조건을 고른다")
    void picksMostExpensiveWithinTarget() {
        // 예산 2.4억. 2억까지는 되고 3억은 안 된다.
        put("11110", HousingType.APT, DealType.JEONSE, 20, 3 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 15, 2 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 10, 1 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 4, 5000 * 만, 0);

        RecommendationItem item = recommend(request("11110", HousingType.APT, DealType.JEONSE));

        assertThat(item.getType()).isEqualTo(AlgorithmType.REALISTIC);
        assertThat(item.getCondition().getAreaMin()).isEqualTo(15);
        assertThat(item.getCondition().getAreaMax()).isEqualTo(19);
        // 화면에 나가는 목표 금액은 실제 보증금이다.
        verify(loanPlanCalculator).calculate(eq(MEMBER_ID), eq(2 * 억), any());
    }

    @Test
    @DisplayName("예산이 늘면 더 좋은 조건으로 올라간다")
    void upgradesWhenBudgetAllows() {
        put("11110", HousingType.APT, DealType.JEONSE, 20, 3 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 15, 2 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 10, 1 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 4, 5000 * 만, 0);

        // 월 저축 2천만 → 예산 4.8억. 이제 3억짜리도 들어온다.
        when(assetSummaryService.getSummary(MEMBER_ID)).thenReturn(
                AssetSummaryResponse.builder().monthlySavings(20_000_000L).build());

        RecommendationItem item = recommend(request("11110", HousingType.APT, DealType.JEONSE));

        assertThat(item.getCondition().getAreaMin()).isEqualTo(20);
    }

    @Test
    @DisplayName("목표 시점 안에 되는 후보가 없으면 가장 가까운 것을 알리고 가능하다고 말하지 않는다")
    void reportsClosestWhenNothingFits() {
        // 예산 2.4억인데 전부 그 위에 있다.
        put("11110", HousingType.APT, DealType.JEONSE, 20, 9 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 15, 7 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 10, 5 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 4, 4 * 억, 0);

        RecommendationItem item = recommend(request("11110", HousingType.APT, DealType.JEONSE));

        // 가장 싼 것 = 목표 시점에 가장 가까운 것
        assertThat(item.getCondition().getAreaMin()).isEqualTo(4);
        assertThat(item.getReason()).contains("40개월이 필요합니다");
    }

    @Test
    @DisplayName("월세는 전세 환산 보증금으로 비교하고, 목표 금액은 실제 보증금으로 내려간다")
    void comparesWolseByConvertedDeposit() {
        // 전세 3억은 예산(2.4억) 초과.
        put("11110", HousingType.APT, DealType.JEONSE, 15, 3 * 억, 0);
        // 월세 보증금 1,000만 + 월세 40만 → 환산 1,000만 + (480만 ÷ 0.05) = 1.06억. 예산 안에 들어온다.
        put("11110", HousingType.APT, DealType.WOLSE, 15, 1000 * 만, 40 * 만);

        GoalRecommendationRequest request = request("11110", HousingType.APT, null);
        request.setSizeMin(15);
        request.setSizeMax(19);

        RecommendationItem item = recommend(request);

        assertThat(item.getCondition().getDealType()).isEqualTo(DealType.WOLSE);
        assertThat(item.getCondition().getMonthlyRent()).isEqualTo(40 * 만);
        // 환산값(1.06억)이 아니라 실제로 모아야 하는 보증금(1,000만)을 넘긴다.
        verify(loanPlanCalculator).calculate(eq(MEMBER_ID), eq(1000 * 만), any());
    }

    @Test
    @DisplayName("환산했을 때 월세가 더 비싸면 전세를 고른다")
    void prefersJeonseWhenWolseIsPricierAfterConversion() {
        put("11110", HousingType.APT, DealType.JEONSE, 15, 2 * 억, 0);
        // 보증금 1,000만 + 월세 10만 → 환산 3,400만. 전세 2억보다 싸므로 전세가 더 좋은 조건이다.
        put("11110", HousingType.APT, DealType.WOLSE, 15, 1000 * 만, 10 * 만);

        GoalRecommendationRequest request = request("11110", HousingType.APT, null);
        request.setSizeMin(15);
        request.setSizeMax(19);

        RecommendationItem item = recommend(request);

        assertThat(item.getCondition().getDealType()).isEqualTo(DealType.JEONSE);
        assertThat(item.getCondition().getMonthlyRent()).isZero();
    }

    @Test
    @DisplayName("시도만 주면 그 안에서 예산에 맞는 가장 좋은 시군구를 고른다")
    void selectsBestAffordableSigungu() {
        when(regionMapper.findCodesBySidoPrefix("11")).thenReturn(List.of("11110", "11140", "11170"));

        // 기준 조합(아파트·전세·15~19평) 시세로 시군구 순위가 정해진다. 예산은 2.4억.
        put("11110", HousingType.APT, DealType.JEONSE, 15, 8 * 억, 0);  // 초과
        put("11140", HousingType.APT, DealType.JEONSE, 15, 2 * 억, 0);  // 가능 — 가장 비쌈
        put("11170", HousingType.APT, DealType.JEONSE, 15, 1 * 억, 0);  // 가능하지만 더 쌈

        RecommendationItem item = recommend(request("11", HousingType.APT, DealType.JEONSE));

        assertThat(item.getCondition().getRegionCode()).isEqualTo("11140");
    }

    @Test
    @DisplayName("사용자가 시군구를 지정하면 지역을 바꾸지 않는다")
    void keepsUserSpecifiedSigungu() {
        put("11110", HousingType.APT, DealType.JEONSE, 15, 2 * 억, 0);

        RecommendationItem item = recommend(request("11110", HousingType.APT, DealType.JEONSE));

        assertThat(item.getCondition().getRegionCode()).isEqualTo("11110");
        verify(regionMapper, never()).findCodesBySidoPrefix(any());
    }

    @Test
    @DisplayName("표본이 부족한 조합은 median을 믿지 않고 건너뛴다")
    void skipsThinSamples() {
        // 가장 비싸지만 표본 2건 → 제외되어야 한다.
        putWithSample("11110", HousingType.APT, DealType.JEONSE, 20, 2 * 억, 0, 2);
        put("11110", HousingType.APT, DealType.JEONSE, 15, 1 * 억, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 10, 5000 * 만, 0);
        put("11110", HousingType.APT, DealType.JEONSE, 4, 3000 * 만, 0);

        RecommendationItem item = recommend(request("11110", HousingType.APT, DealType.JEONSE));

        assertThat(item.getCondition().getAreaMin()).isEqualTo(15);
    }

    @Test
    @DisplayName("월 저축액이 등록되지 않았으면 도달 불가로 흘러간다")
    void treatsMissingMonthlySavingAsZero() {
        when(assetSummaryService.getSummary(MEMBER_ID)).thenReturn(
                AssetSummaryResponse.builder().monthlySavings(null).build());
        put("11110", HousingType.APT, DealType.JEONSE, 15, 2 * 억, 0);

        RecommendationItem item = recommend(request("11110", HousingType.APT, DealType.JEONSE));

        assertThat(item.getReason()).contains("도달하기 어렵습니다");
    }

    @Test
    @DisplayName("입력한 조건으로 목표 시점 안에 되면 더 나은 게 있어도 그대로 둔다")
    void keepsInputConditionWhenItAlreadyFits() {
        // 사용자가 원한 조건. 예산 2.4억 안에 들어온다.
        put("11110", HousingType.OFFICETEL, DealType.JEONSE, 10, 1 * 억, 0);
        // 더 비싸고 예산에도 맞지만, 입력 조건이 이미 되므로 이쪽으로 바꾸면 안 된다.
        put("11110", HousingType.APT, DealType.JEONSE, 20, 2 * 억, 0);

        GoalRecommendationRequest request = request("11110", HousingType.OFFICETEL, DealType.JEONSE);
        request.setSizeMin(10);
        request.setSizeMax(14);

        RecommendationItem item = recommend(request);

        assertThat(item.getCondition().getHousingType()).isEqualTo(HousingType.OFFICETEL);
        assertThat(item.getCondition().getAreaMin()).isEqualTo(10);
        assertThat(item.getReason()).doesNotContain("입력하신 조건으로는");
    }

    @Test
    @DisplayName("입력한 조건으로 목표 시점을 못 지키면 그때 조건을 풀어 대안을 찾는다")
    void relaxesInputConditionOnlyWhenItFails() {
        // 사용자가 원한 조건(아파트 20~25평 전세)은 예산 2.4억을 훨씬 넘는다.
        put("11110", HousingType.APT, DealType.JEONSE, 20, 9 * 억, 0);
        // 같은 구의 더 작은 오피스텔이면 들어온다.
        put("11110", HousingType.OFFICETEL, DealType.JEONSE, 10, 2 * 억, 0);

        GoalRecommendationRequest request = request("11110", HousingType.APT, DealType.JEONSE);
        request.setSizeMin(20);
        request.setSizeMax(25);

        RecommendationItem item = recommend(request);

        // 지역은 그대로, 유형·평수는 조정됐다.
        assertThat(item.getCondition().getRegionCode()).isEqualTo("11110");
        assertThat(item.getCondition().getHousingType()).isEqualTo(HousingType.OFFICETEL);
        assertThat(item.getCondition().getAreaMin()).isEqualTo(10);
        assertThat(item.getReason()).contains("입력하신 조건으로는");
    }

    @Test
    @DisplayName("시군구 순위는 기본값이 아니라 입력한 조건으로 매긴다")
    void ranksRegionsByInputCondition() {
        when(regionMapper.findCodesBySidoPrefix("11")).thenReturn(List.of("11110", "11140"));

        // 사용자가 오피스텔·월세를 원했다. 기본값(아파트·전세)으로 줄을 세우면 종로가 뽑히지만,
        // 입력 조건으로 보면 중구가 더 낫다.
        put("11110", HousingType.APT, DealType.JEONSE, 15, 1 * 억, 0);
        put("11140", HousingType.APT, DealType.JEONSE, 15, 5000 * 만, 0);
        put("11110", HousingType.OFFICETEL, DealType.WOLSE, 15, 500 * 만, 30 * 만);
        put("11140", HousingType.OFFICETEL, DealType.WOLSE, 15, 2000 * 만, 30 * 만);

        RecommendationItem item = recommend(
                request("11", HousingType.OFFICETEL, DealType.WOLSE));

        assertThat(item.getCondition().getRegionCode()).isEqualTo("11140");
    }

    @Test
    @DisplayName("1차와 2차에 겹치는 조합을 다시 조회하지 않는다")
    void doesNotLookUpSameCombinationTwice() {
        // 주거유형만 입력하면 1차는 아파트로 8가지, 2차는 32가지를 보는데 그 8가지가 2차에 포함된다.
        // 어느 것도 예산에 맞지 않게 두어 2차까지 반드시 가도록 한다.
        put("11110", HousingType.APT, DealType.JEONSE, 15, 90 * 억, 0);

        recommend(request("11110", HousingType.APT, null));

        assertThat(lookedUp).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("실거래 표본이 아무 조합에도 없으면 추천하지 않는다")
    void returnsEmptyWhenNoMarketData() {
        Optional<RecommendationItem> result = algorithm.recommend(
                MEMBER_ID, request("11110", HousingType.APT, DealType.JEONSE));

        assertThat(result).isEmpty();
    }

    // ===== 헬퍼 =====

    private RecommendationItem recommend(GoalRecommendationRequest request) {
        return algorithm.recommend(MEMBER_ID, request)
                .orElseThrow(() -> new AssertionError("추천 결과가 비어 있습니다"));
    }

    private GoalRecommendationRequest request(String regionCode, HousingType housingType, DealType dealType) {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode(regionCode);
        request.setPropertyType(housingType);
        request.setTradeType(dealType);
        return request;
    }

    private void put(String regionCode, HousingType housingType, DealType dealType,
            int areaMin, long deposit, long monthlyRent) {
        putWithSample(regionCode, housingType, dealType, areaMin, deposit, monthlyRent, 30);
    }

    private void putWithSample(String regionCode, HousingType housingType, DealType dealType,
            int areaMin, long deposit, long monthlyRent, int sampleCount) {
        market.put(key(regionCode, housingType, dealType, areaMin),
                new long[]{deposit, monthlyRent, sampleCount});
    }

    private String key(String regionCode, HousingType housingType, DealType dealType, int areaMin) {
        return regionCode + "|" + housingType + "|" + dealType + "|" + areaMin;
    }

    /** 등록되지 않은 조합은 표본 0건으로 돌려준다. 실제로도 거래가 없는 조건이 흔하다. */
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
        return builder
                .sampleCount((int) found[2])
                .deposit(Quartile.of(found[0], found[0], found[0]))
                .monthlyRent(found[1] > 0 ? Quartile.of(found[1], found[1], found[1]) : Quartile.empty())
                .build();
    }
}
