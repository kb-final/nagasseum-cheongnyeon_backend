package com.team.independence.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.dto.GoalMarketTrendResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.mapper.GoalMapper;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.service.RegionQueryService;
import com.team.independence.property.service.RentMedianService;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalServiceImplMarketTrendTest {

    @Mock RegionQueryService regionQueryService;
    @Mock AssetConnectionService assetConnectionService;
    @Mock AssetSummaryService assetSummaryService;
    @Mock RentMedianService rentMedianService;
    @Mock GoalMapper goalMapper;
    @Mock GoalHousingMapper goalHousingMapper;
    @Mock GoalMarketTrendCacheStore goalMarketTrendCacheStore;
    @Mock LoanPlanCalculator loanPlanCalculator;

    GoalServiceImpl service;

    private static final Long MEMBER_ID = 1L;
    private static final Long GOAL_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new GoalServiceImpl(
                regionQueryService, assetConnectionService, assetSummaryService, rentMedianService,
<<<<<<< HEAD
                goalMapper, goalHousingMapper, goalMarketTrendCacheStore, null, new BudgetCalculator());
=======
                goalMapper, goalHousingMapper, goalMarketTrendCacheStore, new BudgetCalculator(), loanPlanCalculator);
>>>>>>> e7f9312 (feat: 예산 계산에 기존 대출 월 상환액 차감 반영)
    }

    private Goal goal(long targetAmount, long targetRentMiddleAmount, long monthlySaving) {
        return Goal.builder()
                .id(GOAL_ID)
                .memberId(MEMBER_ID)
                .goalType("HOUSING")
                .targetAmount(targetAmount)
                .targetRentMiddleAmount(targetRentMiddleAmount)
                .targetDate(java.time.LocalDate.now().plusYears(1))
                .monthlySaving(monthlySaving)
                .status("ACTIVE")
                .build();
    }

    private GoalHousing goalHousing() {
        return GoalHousing.builder()
                .goalId(GOAL_ID)
                .regionCode("11680")
                .housingType(HousingType.OFFICETEL)
                .dealType(DealType.JEONSE)
                .areaMin(10)
                .areaMax(20)
                .depositMin(0L)
                .depositMax(500_000_000L)
                .monthlyRentMin(0L)
                .monthlyRentMax(0L)
                .build();
    }

    private RentMedianResponse rentStats(int sampleCount, Long median, String baseEndYm) {
        return RentMedianResponse.builder()
                .regionCode("11680")
                .regionName("서울 강남구")
                .housingType(HousingType.OFFICETEL)
                .dealType(DealType.JEONSE)
                .baseStartYm("202509")
                .baseEndYm(baseEndYm)
                .sampleCount(sampleCount)
                .deposit(sampleCount == 0 ? Quartile.empty() : Quartile.of(median - 1000, median, median + 1000))
                .monthlyRent(Quartile.empty())
                .build();
    }

    @Test
    @DisplayName("getMarketTrend - 캐시 히트면 재계산 없이 캐시 값을 그대로 반환한다")
    void getMarketTrend_cacheHit_returnsWithoutRecompute() {
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(goal(100_000_000L, 90_000_000L, 1_000_000L));
        GoalMarketTrendResponse cached = GoalMarketTrendResponse.builder().targetAmount(100_000_000L).build();
        when(goalMarketTrendCacheStore.find(GOAL_ID)).thenReturn(Optional.of(cached));

        GoalMarketTrendResponse result = service.getMarketTrend(MEMBER_ID);

        assertThat(result).isSameAs(cached);
        verify(rentMedianService, never()).getMedian(any());
        verify(assetSummaryService, never()).getNetWorthBreakdown(anyLong());
        verify(goalMarketTrendCacheStore, never()).save(any(), any());
    }

    @Test
    @DisplayName("getMarketTrend - ACTIVE 목표가 없으면 GOAL_NOT_FOUND")
    void getMarketTrend_noActiveGoal_throws() {
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getMarketTrend(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_NOT_FOUND);
    }

    @Test
    @DisplayName("getMarketTrend - 캐시 미스면 refreshMarketTrend로 계산 후 캐시에 저장한다")
    void getMarketTrend_cacheMiss_computesAndCaches() {
        Goal goal = goal(100_000_000L, 90_000_000L, 1_000_000L);
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(0L);
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(goal);
        when(goalMapper.findById(GOAL_ID)).thenReturn(goal);
        when(goalMarketTrendCacheStore.find(GOAL_ID)).thenReturn(Optional.empty());
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(rentMedianService.getMedian(any(RentMedianRequest.class)))
                .thenReturn(rentStats(5, 95_000_000L, "202607"));
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(200_000_000L)
                        .build());

        GoalMarketTrendResponse result = service.getMarketTrend(MEMBER_ID);

        assertThat(result.getCurrentMiddleAmount()).isEqualTo(95_000_000L);
        assertThat(result.getChangeAmount()).isEqualTo(5_000_000L); // 95M - 90M
        assertThat(result.getUpdatedYm()).isEqualTo(YearMonth.of(2026, 7));
        verify(goalMarketTrendCacheStore).save(eq(GOAL_ID), any(GoalMarketTrendResponse.class));
    }

    @Test
    @DisplayName("refreshMarketTrend - 조건에 맞는 실거래가 없으면 GOAL_NO_MARKET_DATA")
    void refreshMarketTrend_noMarketData_throws() {
        Goal goal = goal(100_000_000L, 90_000_000L, 1_000_000L);
        when(goalMapper.findById(GOAL_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(rentMedianService.getMedian(any(RentMedianRequest.class))).thenReturn(rentStats(0, null, "202607"));

        assertThatThrownBy(() -> service.refreshMarketTrend(GOAL_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_NO_MARKET_DATA);

        verify(goalMarketTrendCacheStore, never()).save(any(), any());
    }

    @Test
    @DisplayName("refreshMarketTrend - 목표가 없으면 GOAL_NOT_FOUND")
    void refreshMarketTrend_goalNotFound_throws() {
        when(goalMapper.findById(GOAL_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.refreshMarketTrend(GOAL_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_NOT_FOUND);
    }

    @Test
    @DisplayName("refreshMarketTrend - 희망 주거 조건이 없으면 GOAL_NOT_FOUND")
    void refreshMarketTrend_goalHousingNotFound_throws() {
        Goal goal = goal(100_000_000L, 90_000_000L, 1_000_000L);
        when(goalMapper.findById(GOAL_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.refreshMarketTrend(GOAL_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_NOT_FOUND);
    }

    @Test
    @DisplayName("refreshMarketTrend - maintainEta는 재계산하지 않고 goal.target_date를 그대로 반환한다")
    void refreshMarketTrend_maintainEta_usesStoredTargetDateAsIs() {
        Goal goal = goal(1L, 90_000_000L, 1_000_000L); // targetAmount=1원, 사실상 이미 달성
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(0L);
        when(goalMapper.findById(GOAL_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(rentMedianService.getMedian(any(RentMedianRequest.class)))
                .thenReturn(rentStats(5, 1L, "202607"));
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(1_000_000_000L)
                        .build());

        GoalMarketTrendResponse result = service.refreshMarketTrend(GOAL_ID);

        // 자산이 충분해 이미 달성 가능한 상황이어도 maintainEta는 재계산되지 않고 저장된 target_date 그대로다
        assertThat(result.getMaintainEta()).isEqualTo(YearMonth.from(goal.getTargetDate()));
        assertThat(result.getReflectEta()).isEqualTo(YearMonth.now());
    }

    @Test
    @DisplayName("refreshMarketTrend - reflectEta는 상한(240개월) 내 도달 불가능하면 null, maintainEta는 그대로 target_date")
    void refreshMarketTrend_unreachable_reflectEtaIsNull() {
        Goal goal = goal(9_999_999_999L, 90_000_000L, 0L); // 저축 0, 목표는 사실상 도달 불가
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(0L);
        when(goalMapper.findById(GOAL_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(rentMedianService.getMedian(any(RentMedianRequest.class)))
                .thenReturn(rentStats(5, 9_999_999_999L, "202607"));
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(0L)
                        .build());

        GoalMarketTrendResponse result = service.refreshMarketTrend(GOAL_ID);

        assertThat(result.getMaintainEta()).isEqualTo(YearMonth.from(goal.getTargetDate()));
        assertThat(result.getReflectEta()).isNull();
    }

    @Test
    @DisplayName("refreshMarketTrend - maintainEta(목표 유지)와 reflectEta(시세 반영)는 금액이 다르면 다른 시점으로 계산된다")
    void refreshMarketTrend_maintainAndReflect_diverge() {
        // targetAmount(100M)가 currentMiddleAmount(10M)보다 훨씬 커서, 반영(reflect) 쪽이 훨씬 빨리 도달해야 한다
        Goal goal = goal(100_000_000L, 90_000_000L, 1_000_000L);
        when(loanPlanCalculator.calcTotalExistingMonthlyPayment(MEMBER_ID)).thenReturn(0L);
        when(goalMapper.findById(GOAL_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(rentMedianService.getMedian(any(RentMedianRequest.class)))
                .thenReturn(rentStats(5, 10_000_000L, "202607"));
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(0L)
                        .build());

        GoalMarketTrendResponse result = service.refreshMarketTrend(GOAL_ID);

        assertThat(result.getMaintainEta()).isNotNull();
        assertThat(result.getReflectEta()).isNotNull();
        assertThat(result.getReflectEta()).isLessThan(result.getMaintainEta());
    }
}
