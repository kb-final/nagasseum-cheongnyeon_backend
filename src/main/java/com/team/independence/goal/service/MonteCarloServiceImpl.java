package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.dto.MonteCarloResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.service.PriceModelService;
import com.team.independence.property.service.RegionQueryService;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonteCarloServiceImpl implements MonteCarloService {

    private final GoalService goalService;
    private final GoalHousingMapper goalHousingMapper;
    private final AssetConnectionService assetConnectionService;
    private final AssetSummaryService assetSummaryService;
    private final BudgetCalculator budgetCalculator;
    private final PriceModelService priceModelService;
    private final RegionQueryService regionQueryService;
    private final MonteCarloSimulationStore simulationStore;

    @Override
    @Transactional(readOnly = true)
    public MonteCarloResponse simulate(Long memberId, Long goalId) {
        // 소유권 확인은 캐시 조회 전에 수행한다
        Goal goal = goalService.findOwnedGoal(memberId, goalId);

        // 캐시 HIT: 목표 조건이 바뀌지 않았으면 재계산 없이 반환
        return simulationStore.find(goalId).orElseGet(() -> {
            log.info("[Monte Carlo] 캐시 MISS: 계산 후 캐싱 goalId={}", goalId);
            MonteCarloResponse response = compute(memberId, goal, goalId);
            simulationStore.save(goalId, response);
            return response;
        });
    }

    private MonteCarloResponse compute(Long memberId, Goal goal, Long goalId) {

        GoalHousing housing = goalHousingMapper.findByGoalId(goalId);
        if (housing == null) {
            throw new BusinessException(ErrorCode.GOAL_NOT_FOUND);
        }

        YearMonth targetDate = YearMonth.from(goal.getTargetDate());
        int months = (int) YearMonth.now().until(targetDate, ChronoUnit.MONTHS);
        if (months <= 0) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_DATE);
        }

        assetConnectionService.validateConnectedAccountExists(memberId);
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long currentBudget = netWorth.getInterestBearingAssets() + netWorth.getFlatRecognizedAssets();
        long budgetAtT = budgetCalculator.calculate(netWorth, goal.getMonthlySaving(), months);

        long initialPrice = goal.getTargetRentMiddleAmount();
        PriceModelRequest priceModelRequest = buildPriceModelRequest(housing);
        PriceModelResponse priceModel = priceModelService.estimate(priceModelRequest);
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                priceModel.getAnnualDrift(), priceModel.getAnnualVol(),
                initialPrice, budgetAtT,
                months, MonteCarloEngine.DEFAULT_SIMULATIONS, MonteCarloEngine.DEFAULT_SEED);

        String regionName = regionQueryService.resolveRegionName(housing.getRegionCode());

        return MonteCarloResponse.builder()
                .goalId(goalId)
                .regionCode(housing.getRegionCode())
                .regionName(regionName)
                .housingType(housing.getHousingType())
                .dealType(housing.getDealType())
                .months(months)
                .targetDate(targetDate)
                .annualDrift(priceModel.getAnnualDrift())
                .cagr(priceModel.getCagr())
                .annualVol(priceModel.getAnnualVol())
                .initialPrice(initialPrice)
                .currentBudget(currentBudget)
                .budgetAtT(budgetAtT)
                .priceP5(result.priceP5())
                .priceP50(result.priceP50())
                .priceP95(result.priceP95())
                .successProbability(result.successProbability())
                .build();
    }

    @Override
    public MonteCarloEngine.Result simulate(PriceModelRequest housing, long initialPrice, long budgetAtT, int months) {
        PriceModelResponse priceModel = priceModelService.estimate(housing);
        return MonteCarloEngine.simulate(
                priceModel.getAnnualDrift(), priceModel.getAnnualVol(),
                initialPrice, budgetAtT,
                months, MonteCarloEngine.DEFAULT_SIMULATIONS, MonteCarloEngine.DEFAULT_SEED);
    }

    private PriceModelRequest buildPriceModelRequest(GoalHousing housing) {
        PriceModelRequest req = new PriceModelRequest();
        req.setRegionCode(housing.getRegionCode());
        req.setHousingType(housing.getHousingType());
        req.setDealType(housing.getDealType());
        req.setAreaMin(housing.getAreaMin());
        req.setAreaMax(housing.getAreaMax());
        return req;
    }
}
