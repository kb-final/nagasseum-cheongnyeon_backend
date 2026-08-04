package com.team.independence.goal.service;

import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.SavingBasis;
import com.team.independence.goal.dto.GoalForecastResponse;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 월 저축액을 바꿨다고 가정했을 때의 예상 달성 시점을 계산한다(저장 없음).
 *
 * <p>목표 상세 조회의 forecasts와 같은 계산·같은 응답 구조를 쓴다.
 * 같은 금액을 넣으면 두 API가 같은 달을 돌려줘야 [월 저축 계획 수정] 팝업 표시가 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GoalSimulationServiceImpl implements GoalSimulationService {

    private static final String GOAL_STATUS_ACTIVE = "ACTIVE";

    private final AssetService assetService;
    private final GoalService goalService; // 소유권 검증 + 진단과 동일한 복리 계산

    @Override
    public GoalForecastResponse simulateMonthlySaving(Long memberId, Long goalId, Long monthlySaving) {
        if (monthlySaving == null || monthlySaving <= 0) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_INPUT);
        }

        Goal goal = goalService.findOwnedGoal(memberId, goalId);
        if (!GOAL_STATUS_ACTIVE.equals(goal.getStatus())) {
            throw new BusinessException(ErrorCode.GOAL_NOT_ACTIVE);
        }

        assetService.validateConnectedAccountExists(memberId);
        AssetNetWorthBreakdown netWorth = assetService.getNetWorthBreakdown(memberId);
        long currentAmount = netWorth.getInterestBearingAssets() + netWorth.getFlatRecognizedAssets();

        long targetAmount = goal.getTargetAmount();

        // 이미 달성했으면 예상 시점을 따질 게 없다(상세 조회와 동일한 처리).
        if (currentAmount >= targetAmount) {
            return buildResponse(monthlySaving, null, 0);
        }

        Long customMonths = goalService.calculateMonthToReach(netWorth, monthlySaving, targetAmount);
        Long fixedMonths = calculateFixedMonths(netWorth, goal, targetAmount);

        YearMonth expectedDate = customMonths == null ? null : YearMonth.now().plusMonths(customMonths);
        Integer monthsDiff = (fixedMonths == null || customMonths == null)
                ? null
                : (int) (fixedMonths - customMonths);

        return buildResponse(monthlySaving, expectedDate, monthsDiff);
    }

    /** 비교 기준이 되는 고정 저축액의 도달 개월수. 저축액이 0 이하면 비교할 수 없다. */
    private Long calculateFixedMonths(AssetNetWorthBreakdown netWorth, Goal goal, long targetAmount) {
        Long fixedSaving = goal.getMonthlySaving();
        if (fixedSaving == null || fixedSaving <= 0) {
            return null;
        }
        return goalService.calculateMonthToReach(netWorth, fixedSaving, targetAmount);
    }

    private GoalForecastResponse buildResponse(Long monthlySaving, YearMonth expectedDate, Integer monthsDiff) {
        return GoalForecastResponse.builder()
                .basis(SavingBasis.CUSTOM)
                .monthlySaving(monthlySaving)
                .expectedDate(expectedDate)
                .monthsDiff(monthsDiff)
                .build();
    }
}
