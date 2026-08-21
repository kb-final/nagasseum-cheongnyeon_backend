package com.team.independence.goal.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.service.RecommendationAlgorithm.MemberFinancialContext;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.GoalRecommendationResponse.RecommendationItem;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.service.MonteCarloService;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.goal.service.calculator.LoanSchedule;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.service.RentMedianService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 선호 우선 추천 알고리즘 테스트.
 *
 * <p>이 카드는 판단하지 않고 그대로 옮기는 것이 일이라, 검증도 "입력이 조회 조건으로 그대로
 * 흘러갔는가"와 "조회 결과가 응답으로 그대로 나왔는가"에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreferenceAlgorithmTest {

    private static final long MEMBER_ID = 1L;
    private static final long 억 = 100_000_000L;
    private static final long 만 = 10_000L;

    @Mock
    private RentMedianService rentMedianService;
    @Mock
    private LoanPlanCalculator loanPlanCalculator;
    @Mock
    private BudgetCalculator budgetCalculator;
    @Mock
    private MonteCarloService monteCarloService;

    private PreferenceAlgorithm algorithm;
    private AssetNetWorthBreakdown defaultNetWorth;
    private MemberFinancialContext ctx;

    /** 실제로 나간 실거래 조회 조건 */
    private List<RentMedianRequest> asked;

    @BeforeEach
    void setUp() {
        algorithm = new PreferenceAlgorithm(rentMedianService, loanPlanCalculator, budgetCalculator, monteCarloService);
        asked = new ArrayList<>();
        defaultNetWorth = AssetNetWorthBreakdown.builder()
                .interestBearingAssets(0L)
                .flatRecognizedAssets(0L)
                .build();
        ctx = new MemberFinancialContext(defaultNetWorth, 10_000_000L, List.of());

        when(loanPlanCalculator.calculate(anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(LoanPlans.builder().build());
        when(loanPlanCalculator.calculateSavingFixed(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(LoanPlans.builder().build());
        stubMedian(2 * 억, 0, 120);
    }

    @Test
    @DisplayName("입력한 조건을 그대로 조회 조건으로 넘긴다")
    void passesInputConditionThrough() {
        GoalRecommendationRequest request = request("11110");
        request.setPropertyType(HousingType.OFFICETEL);
        request.setTradeType(DealType.WOLSE);
        request.setSizeMin(10);
        request.setSizeMax(14);

        RecommendationItem item = recommend(request);

        RentMedianRequest sent = asked.get(0);
        assertThat(sent.getRegionCode()).isEqualTo("11110");
        assertThat(sent.getHousingType()).isEqualTo(HousingType.OFFICETEL);
        assertThat(sent.getDealType()).isEqualTo(DealType.WOLSE);
        assertThat(sent.getAreaMin()).isEqualTo(10);
        assertThat(sent.getAreaMax()).isEqualTo(14);

        assertThat(item.getType()).isEqualTo(AlgorithmType.PREFERENCE_SAVING_FIXED);
        assertThat(item.getCondition().getHousingType()).isEqualTo(HousingType.OFFICETEL);
        assertThat(item.getCondition().getAreaMin()).isEqualTo(10);
    }

    @Test
    @DisplayName("빈 칸은 고정 기본값으로 채운다")
    void fillsBlanksWithFixedDefaults() {
        RecommendationItem item = recommend(request("11110"));

        RentMedianRequest sent = asked.get(0);
        assertThat(sent.getHousingType()).isEqualTo(HousingType.APT);
        assertThat(sent.getDealType()).isEqualTo(DealType.JEONSE);
        assertThat(sent.getAreaMin()).isEqualTo(15);
        assertThat(sent.getAreaMax()).isEqualTo(19);
        assertThat(item.getCondition().getAreaMax()).isEqualTo(19);
    }

    @Test
    @DisplayName("감당할 수 없는 금액이어도 조정하지 않고 그대로 낸다")
    void reportsUnaffordableConditionAsIs() {
        stubMedian(90 * 억, 0, 40);

        RecommendationItem item = recommend(request("11110"));

        // 예산을 보지 않으므로 조회 횟수는 언제나 1회이고, 조건도 바뀌지 않는다.
        assertThat(asked).hasSize(1);
        assertThat(item.getCondition().getHousingType()).isEqualTo(HousingType.APT);
        assertThat(item.getCondition().getAreaMin()).isEqualTo(15);
    }

    @Test
    @DisplayName("시도를 받으면 시군구를 고르지 않고 시도 전체로 답한다")
    void answersWithWholeSidoWhenGivenSidoCode() {
        stubMedian("11", "서울특별시", 6 * 억, 0, 8200);

        RecommendationItem item = recommend(request("11"));

        assertThat(asked).hasSize(1);
        assertThat(asked.get(0).getRegionCode()).isEqualTo("11");
        assertThat(item.getCondition().getRegionCode()).isEqualTo("11");
        assertThat(item.getCondition().getRegionName()).isEqualTo("서울특별시");
    }

    @Test
    @DisplayName("표본이 적어도 버리지 않고 건수를 함께 내려준다")
    void keepsThinSampleAndReportsCount() {
        stubMedian(3 * 억, 0, 2);

        RecommendationItem item = recommend(request("11110"));

        assertThat(item.getCondition().getSampleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("월세면 월세 금액을 조건에 담고, 목표 금액은 보증금으로 넘긴다")
    void carriesMonthlyRentAndUsesDepositAsTarget() {
        stubMedian(1000 * 만, 40 * 만, 60);

        GoalRecommendationRequest request = request("11110");
        request.setTradeType(DealType.WOLSE);

        RecommendationItem item = recommend(request);

        assertThat(item.getCondition().getMonthlyRent()).isEqualTo(40 * 만);
        // 환산값이 아니라 실제 보증금(1,000만)을 플랜 계산기로 넘긴다. targetDate가 없어 저축 고정 경로만 탄다.
        verify(loanPlanCalculator).calculateSavingFixed(eq(MEMBER_ID), eq(1000 * 만), any(), anyLong(), any());
    }

    @Test
    @DisplayName("월세면 저축 고정·시점 고정 두 카드 모두 monthlySaving에 월세를 더한다")
    void addsMonthlyRentToBothCardsWhenWolse() {
        stubMedian(1000 * 만, 40 * 만, 60);
        when(loanPlanCalculator.calculateSavingFixed(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(LoanPlans.builder()
                        .loanX(GoalRecommendationResponse.LoanXPlan.builder()
                                .targetAmount(1000 * 만).monthlySaving(1_000_000L).build())
                        .build());
        when(loanPlanCalculator.calculate(anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(LoanPlans.builder()
                        .loanX(GoalRecommendationResponse.LoanXPlan.builder()
                                .targetAmount(1000 * 만).monthlySaving(500_000L).build())
                        .build());

        GoalRecommendationRequest request = request("11110");
        request.setTradeType(DealType.WOLSE);
        request.setTargetDate(java.time.YearMonth.now().plusMonths(24));

        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request, ctx);

        // 저축 고정: 목(100만) + 월세(40만) = 140만
        assertThat(result.get(0).getLoanX().getMonthlySaving()).isEqualTo(1_400_000L);
        // 시점 고정: 목(50만) + 월세(40만) = 90만
        assertThat(result.get(1).getLoanX().getMonthlySaving()).isEqualTo(900_000L);
    }

    @Test
    @DisplayName("전세면 monthlySaving을 그대로 둔다")
    void keepsMonthlySavingUnchangedWhenJeonse() {
        when(loanPlanCalculator.calculateSavingFixed(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(LoanPlans.builder()
                        .loanX(GoalRecommendationResponse.LoanXPlan.builder()
                                .targetAmount(2 * 억).monthlySaving(1_000_000L).build())
                        .build());

        RecommendationItem item = recommend(request("11110"));

        assertThat(item.getLoanX().getMonthlySaving()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("실거래가 한 건도 없으면 카드를 빼지 않고 condition=null 두 장을 낸다")
    void returnsNullCardsWhenNoTransaction() {
        stubMedian(0, 0, 0);

        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request("11110"), ctx);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RecommendationItem::getType)
                .containsExactly(AlgorithmType.PREFERENCE_SAVING_FIXED, AlgorithmType.PREFERENCE_DATE_FIXED);
        assertThat(result).allSatisfy(item -> assertThat(item.getCondition()).isNull());
    }

    @Test
    @DisplayName("실거래 조회가 실패해도 예외를 던지지 않고 condition=null 두 장을 낸다")
    void returnsNullCardsWhenLookUpFails() {
        doThrow(new IllegalStateException("조회 실패")).when(rentMedianService).getMedian(any());

        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request("11110"), ctx);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(item -> assertThat(item.getCondition()).isNull());
    }

    @Test
    @DisplayName("목표 시점이 없으면 저축 고정은 정상, 시점 고정은 조건 없는 카드로 두 장을 낸다")
    void splitsIntoTwoCardsAndDateFixedIsNullWithoutTargetDate() {
        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request("11110"), ctx);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getType()).isEqualTo(AlgorithmType.PREFERENCE_SAVING_FIXED);
        assertThat(result.get(0).getCondition()).isNotNull();
        assertThat(result.get(1).getType()).isEqualTo(AlgorithmType.PREFERENCE_DATE_FIXED);
        assertThat(result.get(1).getCondition()).isNull();
    }

    @Test
    @DisplayName("목표 시점이 있으면 두 카드 모두 조건을 채우고, 시점 고정은 그 시점으로 플랜을 계산한다")
    void bothCardsHaveConditionWhenTargetDateGiven() {
        GoalRecommendationRequest request = request("11110");
        request.setTargetDate(java.time.YearMonth.now().plusMonths(24));

        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request, ctx);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).getType()).isEqualTo(AlgorithmType.PREFERENCE_DATE_FIXED);
        assertThat(result.get(1).getCondition()).isNotNull();
        verify(loanPlanCalculator).calculate(eq(MEMBER_ID), eq(2 * 억), any(), anyLong());
    }

    // ===== 헬퍼 =====

    /** 두 카드 중 첫 번째(SAVING_FIXED). 조건은 두 카드가 공유하므로 조건 검증엔 어느 쪽이든 무방하다. */
    private RecommendationItem recommend(GoalRecommendationRequest request) {
        List<RecommendationItem> result = algorithm.recommend(MEMBER_ID, request, ctx);
        if (result.isEmpty()) throw new AssertionError("추천 결과가 비어 있습니다");
        return result.get(0);
    }

    private GoalRecommendationRequest request(String regionCode) {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode(regionCode);
        return request;
    }

    private void stubMedian(long deposit, long monthlyRent, int sampleCount) {
        stubMedian("11110", "서울특별시 종로구", deposit, monthlyRent, sampleCount);
    }

    private void stubMedian(String regionCode, String regionName,
            long deposit, long monthlyRent, int sampleCount) {
        doAnswer(call -> {
            RentMedianRequest req = call.getArgument(0);
            asked.add(req);
            return RentMedianResponse.builder()
                    .regionCode(regionCode)
                    .regionName(regionName)
                    .housingType(req.getHousingType())
                    .dealType(req.getDealType())
                    .sampleCount(sampleCount)
                    .deposit(sampleCount == 0 ? Quartile.empty() : Quartile.of(deposit, deposit, deposit))
                    .monthlyRent(monthlyRent > 0
                            ? Quartile.of(monthlyRent, monthlyRent, monthlyRent) : Quartile.empty())
                    .build();
        }).when(rentMedianService).getMedian(any());
    }
}
