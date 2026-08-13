package com.team.independence.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.dto.MonteCarloResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.service.PriceModelService;
import com.team.independence.property.service.RegionQueryService;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MonteCarloServiceImpl 서비스 로직 단위 테스트.
 *
 * DB 없이 테스트 수행. 의존 서비스를 Mockito로 목 처리하고,
 * 서비스의 조립, 검증, 예외 경로만 검증한다.
 * MonteCarloEngine 계산 자체는 MonteCarloEngineTest에서 별도로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MonteCarloServiceImplTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long GOAL_ID = 10L;
    private static final String REGION_CODE = "11680";
    private static final String REGION_NAME = "서울특별시 강남구";
    private static final long INITIAL_PRICE = 300_000_000L;

    @Mock private GoalService goalService;
    @Mock private GoalHousingMapper goalHousingMapper;
    @Mock private AssetConnectionService assetConnectionService;
    @Mock private AssetSummaryService assetSummaryService;
    @Mock private BudgetCalculator budgetCalculator;
    @Mock private PriceModelService priceModelService;
    @Mock private RegionQueryService regionQueryService;
    @Mock private MonteCarloSimulationStore simulationStore;

    @InjectMocks private MonteCarloServiceImpl service;

    private Goal goal;
    private GoalHousing housing;
    private AssetNetWorthBreakdown netWorth;

    @BeforeEach
    void setUp() {
        goal = Goal.builder()
                .id(GOAL_ID)
                .memberId(MEMBER_ID)
                .targetAmount(400_000_000L)
                .targetRentMiddleAmount(INITIAL_PRICE)
                .targetDate(YearMonth.now().plusMonths(24).atDay(1))
                .monthlySaving(1_000_000L)
                .status("ACTIVE")
                .build();

        housing = GoalHousing.builder()
                .goalId(GOAL_ID)
                .regionCode(REGION_CODE)
                .housingType(HousingType.APT)
                .dealType(DealType.JEONSE)
                .areaMin(15)
                .areaMax(25)
                .build();

        netWorth = AssetNetWorthBreakdown.builder()
                .interestBearingAssets(50_000_000L)
                .flatRecognizedAssets(20_000_000L)
                .build();

        // 기본적으로 캐시 미스로 처리: 실제 계산 경로를 검증한다
        when(simulationStore.find(any())).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------
    // 정상 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("정상 경로 → 응답 필드가 올바르게 조립된다")
    void 정상_경로_응답_조립() {
        stubHappyPath(0.05, 0.10, 250_000_000L);

        MonteCarloResponse response = service.simulate(MEMBER_ID, GOAL_ID);

        assertEquals(GOAL_ID, response.getGoalId());
        assertEquals(REGION_CODE, response.getRegionCode());
        assertEquals(REGION_NAME, response.getRegionName());
        assertEquals(HousingType.APT, response.getHousingType());
        assertEquals(DealType.JEONSE, response.getDealType());
        assertEquals(INITIAL_PRICE, response.getInitialPrice());
        assertEquals(70_000_000L, response.getCurrentBudget());
        assertTrue(response.getMonths() > 0);
        assertTrue(response.getSuccessProbability() >= 0.0 && response.getSuccessProbability() <= 1.0);
    }

    @Test
    @DisplayName("σ=0 → P5 == P50 == P95 (결정적 경로)")
    void 변동성_0_결정적() {
        stubHappyPath(0.0, 0.0, 1_000_000_000L);

        MonteCarloResponse response = service.simulate(MEMBER_ID, GOAL_ID);

        assertEquals(response.getPriceP5(), response.getPriceP50(), "σ=0 → P5 == P50");
        assertEquals(response.getPriceP50(), response.getPriceP95(), "σ=0 → P50 == P95");
    }

    @Test
    @DisplayName("예산이 충분히 크면 성공 확률 ≈ 1.0")
    void 예산_충분하면_성공률_1() {
        stubHappyPath(0.05, 0.15, Long.MAX_VALUE / 2);

        MonteCarloResponse response = service.simulate(MEMBER_ID, GOAL_ID);

        assertEquals(1.0, response.getSuccessProbability(), 0.001,
                "압도적 예산 → 성공률 ≈ 1");
    }

    // ------------------------------------------------------------------
    // 예외 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("목표 시점이 현재 이전 → GOAL_INVALID_DATE")
    void 목표_시점_과거_예외() {
        Goal expired = Goal.builder()
                .id(GOAL_ID)
                .memberId(MEMBER_ID)
                .targetRentMiddleAmount(INITIAL_PRICE)
                .monthlySaving(1_000_000L)
                .targetDate(YearMonth.now().minusMonths(1).atDay(1))
                .status("ACTIVE")
                .build();
        when(goalService.findOwnedGoal(MEMBER_ID, GOAL_ID)).thenReturn(expired);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(housing);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.simulate(MEMBER_ID, GOAL_ID));
        assertEquals(ErrorCode.GOAL_INVALID_DATE, e.getErrorCode());
    }

    @Test
    @DisplayName("GoalHousing이 없으면 GOAL_NOT_FOUND")
    void 주거조건_없으면_예외() {
        when(goalService.findOwnedGoal(MEMBER_ID, GOAL_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.simulate(MEMBER_ID, GOAL_ID));
        assertEquals(ErrorCode.GOAL_NOT_FOUND, e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    private void stubHappyPath(double cagr, double annualVol, long budgetAtT) {
        when(goalService.findOwnedGoal(MEMBER_ID, GOAL_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(housing);
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(netWorth);
        when(budgetCalculator.calculate(eq(netWorth), anyLong(), anyLong())).thenReturn(budgetAtT);
        when(priceModelService.estimate(any())).thenReturn(PriceModelResponse.builder()
                .regionCode(REGION_CODE)
                .regionName(REGION_NAME)
                .housingType(HousingType.APT)
                .dealType(DealType.JEONSE)
                .annualDrift(Math.log(1 + cagr))
                .cagr(cagr)
                .annualVol(annualVol)
                .months(24)
                .startYm("202406")
                .endYm("202606")
                .build());
        when(regionQueryService.resolveRegionName(REGION_CODE)).thenReturn(REGION_NAME);
    }
}
