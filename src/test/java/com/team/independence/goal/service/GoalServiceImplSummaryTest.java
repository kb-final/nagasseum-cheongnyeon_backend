package com.team.independence.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.dto.GoalSummaryResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.mapper.GoalMapper;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.service.RegionQueryService;
import com.team.independence.property.service.RentMedianService;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalServiceImplSummaryTest {

    @Mock RegionQueryService regionQueryService;
    @Mock AssetService assetService;
    @Mock AssetSummaryService assetSummaryService;
    @Mock RentMedianService rentMedianService;
    @Mock GoalMapper goalMapper;
    @Mock GoalHousingMapper goalHousingMapper;
    @Mock GoalMarketTrendCacheStore goalMarketTrendCacheStore;

    GoalServiceImpl service;

    private static final Long MEMBER_ID = 1L;
    private static final Long GOAL_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new GoalServiceImpl(
                regionQueryService, assetService, assetSummaryService, rentMedianService,
                goalMapper, goalHousingMapper, goalMarketTrendCacheStore);
    }

    private Goal goal(long targetAmount, long monthlySaving) {
        return goal(targetAmount, monthlySaving, LocalDate.now().plusYears(1));
    }

    private Goal goal(long targetAmount, long monthlySaving, LocalDate targetDate) {
        return Goal.builder()
                .id(GOAL_ID)
                .memberId(MEMBER_ID)
                .goalType("HOUSING")
                .targetAmount(targetAmount)
                .targetRentMiddleAmount(90_000_000L)
                .targetDate(targetDate)
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

    @Test
    @DisplayName("getSummary - 목표/주거조건/진행상황을 모두 채워 반환한다")
    void getSummary_returnsFullSummary() {
        Goal goal = goal(100_000_000L, 1_000_000L);
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(assetService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(20_000_000L)
                        .build());

        GoalSummaryResponse result = service.getSummary(MEMBER_ID);

        assertThat(result.getGoalId()).isEqualTo(GOAL_ID);
        assertThat(result.getGoalType()).isEqualTo("HOUSING");
        assertThat(result.getTargetAmount()).isEqualTo(100_000_000L);
        assertThat(result.getTargetDate()).isEqualTo(YearMonth.from(goal.getTargetDate()));

        assertThat(result.getHousing().getRegionName()).isEqualTo("서울 강남구");
        assertThat(result.getHousing().getHousingType()).isEqualTo(HousingType.OFFICETEL);
        assertThat(result.getHousing().getDealType()).isEqualTo(DealType.JEONSE);
        assertThat(result.getHousing().getAreaMin()).isEqualTo(10);
        assertThat(result.getHousing().getAreaMax()).isEqualTo(20);

        assertThat(result.getProgress().getCurrentAmount()).isEqualTo(20_000_000L);
        assertThat(result.getProgress().getRemainingAmount()).isEqualTo(80_000_000L);
        assertThat(result.getProgress().getAchievementRate()).isEqualTo(20.0);
        // targetDate가 오늘부터 정확히 1년 뒤이므로 재계산 없이 12개월이 그대로 나온다
        assertThat(result.getProgress().getRemainingMonths()).isEqualTo(12L);
    }

    @Test
    @DisplayName("getSummary - 활성 목표가 없으면 GOAL_NOT_FOUND")
    void getSummary_noActiveGoal_throws() {
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getSummary(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_NOT_FOUND);
    }

    @Test
    @DisplayName("getSummary - 희망 주거 조건이 없으면 GOAL_NOT_FOUND")
    void getSummary_goalHousingNotFound_throws() {
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(goal(100_000_000L, 1_000_000L));
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getSummary(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_NOT_FOUND);
    }

    @Test
    @DisplayName("getSummary - 현재 자금이 목표 금액을 넘으면 remainingAmount 0, achievementRate 100으로 clamp")
    void getSummary_overachieved_clampsToZeroAndHundred() {
        Goal goal = goal(50_000_000L, 1_000_000L);
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(assetService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(80_000_000L)
                        .build());

        GoalSummaryResponse result = service.getSummary(MEMBER_ID);

        assertThat(result.getProgress().getRemainingAmount()).isEqualTo(0L);
        assertThat(result.getProgress().getAchievementRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("getSummary - remainingMonths는 저축액과 무관하게 저장된 target_date로만 계산된다")
    void getSummary_remainingMonths_derivedFromStoredTargetDate() {
        // 저축액 0(재계산이었다면 도달 불가로 null이 나왔을 상황)이어도 targetDate 기준 개월수가 그대로 나와야 한다
        Goal goal = goal(100_000_000L, 0L, LocalDate.now().plusMonths(5));
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(assetService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(20_000_000L)
                        .build());

        GoalSummaryResponse result = service.getSummary(MEMBER_ID);

        assertThat(result.getProgress().getRemainingMonths()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getSummary - target_date가 이미 지났으면 remainingMonths는 0으로 clamp")
    void getSummary_targetDatePassed_remainingMonthsClampsToZero() {
        Goal goal = goal(100_000_000L, 1_000_000L, LocalDate.now().minusMonths(3));
        when(goalMapper.findActiveByMemberId(MEMBER_ID)).thenReturn(goal);
        when(goalHousingMapper.findByGoalId(GOAL_ID)).thenReturn(goalHousing());
        when(regionQueryService.resolveRegionName("11680")).thenReturn("서울 강남구");
        when(assetService.getNetWorthBreakdown(MEMBER_ID))
                .thenReturn(AssetNetWorthBreakdown.builder()
                        .interestBearingAssets(0L)
                        .flatRecognizedAssets(20_000_000L)
                        .build());

        GoalSummaryResponse result = service.getSummary(MEMBER_ID);

        assertThat(result.getProgress().getRemainingMonths()).isEqualTo(0L);
    }
}
