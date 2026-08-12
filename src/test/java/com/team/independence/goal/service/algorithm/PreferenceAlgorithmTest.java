package com.team.independence.goal.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.independence.goal.dto.AlgorithmType;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse.RecommendationItem;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.service.LoanPlanCalculator;
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

    private PreferenceAlgorithm algorithm;

    /** 실제로 나간 실거래 조회 조건 */
    private List<RentMedianRequest> asked;

    @BeforeEach
    void setUp() {
        algorithm = new PreferenceAlgorithm(rentMedianService, loanPlanCalculator);
        asked = new ArrayList<>();

        when(loanPlanCalculator.calculate(anyLong(), anyLong(), any()))
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

        assertThat(item.getType()).isEqualTo(AlgorithmType.PREFERENCE);
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
        assertThat(item.getReason()).contains("2건");
    }

    @Test
    @DisplayName("월세면 월세 금액을 조건에 담고, 목표 금액은 보증금으로 넘긴다")
    void carriesMonthlyRentAndUsesDepositAsTarget() {
        stubMedian(1000 * 만, 40 * 만, 60);

        GoalRecommendationRequest request = request("11110");
        request.setTradeType(DealType.WOLSE);

        RecommendationItem item = recommend(request);

        assertThat(item.getCondition().getMonthlyRent()).isEqualTo(40 * 만);
        verify(loanPlanCalculator).calculate(eq(MEMBER_ID), eq(1000 * 만), any());
    }

    @Test
    @DisplayName("실거래가 한 건도 없으면 추천하지 않는다")
    void returnsEmptyWhenNoTransaction() {
        stubMedian(0, 0, 0);

        assertThat(algorithm.recommend(MEMBER_ID, request("11110"))).isEmpty();
    }

    @Test
    @DisplayName("실거래 조회가 실패해도 예외를 밖으로 던지지 않는다")
    void returnsEmptyWhenLookUpFails() {
        doThrow(new IllegalStateException("조회 실패")).when(rentMedianService).getMedian(any());

        assertThat(algorithm.recommend(MEMBER_ID, request("11110"))).isEmpty();
    }

    // ===== 헬퍼 =====

    private RecommendationItem recommend(GoalRecommendationRequest request) {
        return algorithm.recommend(MEMBER_ID, request)
                .orElseThrow(() -> new AssertionError("추천 결과가 비어 있습니다"));
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
