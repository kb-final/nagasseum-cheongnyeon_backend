package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.domain.SavingBasis;
import com.team.independence.goal.domain.SavingRecord;
import com.team.independence.goal.dto.GoalDetailResponse;
import com.team.independence.goal.dto.GoalForecastResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.mapper.SavingRecordMapper;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 목표 상세 화면 데이터를 모아서 반환한다.
 * 예상 달성 시점(forecasts)은 저축액 세 개에 대한 시뮬레이션이다 —
 * 계산은 GoalService가, 응답 조립은 GoalForecastResponse가 맡아
 * 시뮬레이션 API와 같은 금액에 같은 날짜를 낸다.
 */
@Service
@RequiredArgsConstructor
public class GoalDetailServiceImpl implements GoalDetailService {

    /** 최근 평균 저축액을 낼 때 보는 개월 수. 이보다 기록이 적으면 평균을 내지 않는다. */
    private static final int RECENT_MONTHS = 3;

    private final GoalHousingMapper goalHousingMapper;
    private final SavingRecordMapper savingRecordMapper;
    private final AssetConnectionService assetConnectionService;
    private final AssetSummaryService assetSummaryService;
    private final GoalService goalService; // 목표 조회·소유권 검증, 도달 개월수 계산

    @Override
    public GoalDetailResponse getGoalDetail(Long memberId, Long goalId) {
        Goal goal = goalService.findOwnedGoal(memberId, goalId);
        GoalHousing housing = goalHousingMapper.findByGoalId(goalId);

        assetConnectionService.validateConnectedAccountExists(memberId);
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long currentAmount = netWorth.getInterestBearingAssets() + netWorth.getFlatRecognizedAssets();

        long targetAmount = goal.getTargetAmount();
        long remainingAmount = Math.max(0, targetAmount - currentAmount);


        List<SavingRecord> recentRecords = savingRecordMapper.findRecentByGoalId(goalId, RECENT_MONTHS);
        GoalDetailResponse.SavingStatus savingStatus = buildSavingStatus(goal, recentRecords);

        return GoalDetailResponse.builder()
                .goalId(goal.getId())
                .goalType(goal.getGoalType())
                .status(goal.getStatus())
                .targetDate(YearMonth.from(goal.getTargetDate()))
                .housing(buildHousing(housing))
                .progress(GoalDetailResponse.Progress.builder()
                        .targetAmount(targetAmount)
                        .currentAmount(currentAmount)
                        .remainingAmount(remainingAmount)
                        .achievementRate(calculateAchievementRate(currentAmount, targetAmount))
                        .build())
                .savingStatus(savingStatus)
                .forecasts(buildForecasts(netWorth, targetAmount, savingStatus))
                .build();
    }

    private GoalDetailResponse.Housing buildHousing(GoalHousing housing) {
        if (housing == null) {
            return null;
        }
        return GoalDetailResponse.Housing.builder()
                .regionCode(housing.getRegionCode())
                .housingType(housing.getHousingType())
                .dealType(housing.getDealType())
                .areaMin(housing.getAreaMin())
                .areaMax(housing.getAreaMax())
                .depositMin(housing.getDepositMin())
                .depositMax(housing.getDepositMax())
                .build();
    }

    /**
     * 고정 저축액은 목표에서, 최근 저축액은 기록에서 뽑는다.
     * 기록이 RECENT_MONTHS건에 못 미치면 평균은 신뢰할 수 없다고 보고 내지 않는다.
     */
    private GoalDetailResponse.SavingStatus buildSavingStatus(Goal goal, List<SavingRecord> recentRecords) {
        Long latestSaving = recentRecords.isEmpty() ? null : recentRecords.get(0).getActualSaving();

        Long recentAverageSaving = null;
        if (recentRecords.size() >= RECENT_MONTHS) {
            long sum = 0;
            for (SavingRecord record : recentRecords) {
                sum += record.getActualSaving();
            }
            recentAverageSaving = Math.round((double) sum / recentRecords.size());
        }

        return GoalDetailResponse.SavingStatus.builder()
                .fixedSaving(goal.getMonthlySaving())
                .recentAverageSaving(recentAverageSaving)
                .latestSaving(latestSaving)
                .build();
    }

    /** 달성률(%). 0~100으로 자른다 — 또래 비교 집계(goal_snapshot)와 같은 규칙. */
    private Double calculateAchievementRate(long currentAmount, long targetAmount) {
        if (targetAmount <= 0) {
            return 0.0;
        }
        double rate = currentAmount * 100.0 / targetAmount;
        double clamped = Math.min(100.0, Math.max(0.0, rate));
        return Math.round(clamped * 100) / 100.0;
    }

    /**
     * 저축 기준별 예상 달성 시점.
     * 기준 금액이 없거나 0 이하면 계산이 불가하므로 목록에서 뺀다.
     * 항목 하나를 만드는 일은 시뮬레이션 API와 공유해야 해서 GoalService에 맡긴다.
     */
    private List<GoalForecastResponse> buildForecasts(AssetNetWorthBreakdown netWorth,
                                                      long targetAmount,
                                                      GoalDetailResponse.SavingStatus savingStatus) {
        Long fixedMonths = calculateMonths(netWorth, savingStatus.getFixedSaving(), targetAmount);

        List<GoalForecastResponse> forecasts = new ArrayList<>();
        addForecast(forecasts, SavingBasis.FIXED, savingStatus.getFixedSaving(),
                netWorth, targetAmount, fixedMonths);
        addForecast(forecasts, SavingBasis.RECENT_AVERAGE, savingStatus.getRecentAverageSaving(),
                netWorth, targetAmount, fixedMonths);
        addForecast(forecasts, SavingBasis.LATEST, savingStatus.getLatestSaving(),
                netWorth, targetAmount, fixedMonths);
        return forecasts;
    }

    private void addForecast(List<GoalForecastResponse> forecasts,
                             SavingBasis basis,
                             Long monthlySaving,
                             AssetNetWorthBreakdown netWorth,
                             long targetAmount,
                             Long fixedMonths) {
        if (monthlySaving == null || monthlySaving <= 0) {
            return;
        }
        Long months = goalService.calculateMonthToReach(netWorth, monthlySaving, targetAmount);
        forecasts.add(GoalForecastResponse.of(basis, monthlySaving, months, fixedMonths));
    }

    private Long calculateMonths(AssetNetWorthBreakdown netWorth, Long monthlySaving, long targetAmount) {
        if (monthlySaving == null || monthlySaving <= 0) {
            return null;
        }
        return goalService.calculateMonthToReach(netWorth, monthlySaving, targetAmount);
    }
}
