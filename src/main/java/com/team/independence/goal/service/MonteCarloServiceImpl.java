package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.common.diagnostics.RecommendationProfiler;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.dto.MonteCarloResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.service.PriceModelService;
import com.team.independence.property.service.RegionQueryService;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonteCarloServiceImpl implements MonteCarloService {

    /**
     * MC 결과 메모이제이션. {@link MonteCarloEngine#DEFAULT_SEED}가 고정 상수라
     * (annualDrift, annualVol, initialPrice, budgetAtT, months)가 같으면 결과도 항상 같다 — 순수 함수.
     *
     * <p>Preference·Realistic·HoldOut 세 알고리즘이 같은 조합(주거유형·거래유형·평수, 같은 목표시점)을
     * 각자 따로 평가하면서 이 메서드를 공유 호출하므로, 알고리즘 간에도 같은 입력이 반복되는 경우가 많다.
     * 특히 HoldOut은(수렴 루프 하는 다른 곳과 달리) 이 지점에서 내부 중복 제거를 하지 않는다.
     *
     * <p>키 공간이 지역·요청 조건에 따라 갈리긴 하지만 무한정 커지진 않아, 단순 Map으로 두고
     * 크기가 넘치면 통째로 비운다. 정교한 축출(LRU/TTL)이 필요해지면 Caffeine 도입을 검토한다.
     */
    private static final ConcurrentHashMap<McKey, MonteCarloEngine.Result> MC_CACHE = new ConcurrentHashMap<>();
    private static final int MC_CACHE_MAX_SIZE = 5_000;

    private record McKey(double annualDrift, double annualVol, long initialPrice, long budgetAtT, int months) {}

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
        return simulate(priceModel, initialPrice, budgetAtT, months);
    }

    @Override
    public MonteCarloEngine.Result simulate(PriceModelResponse priceModel, long initialPrice, long budgetAtT, int months) {
        McKey key = new McKey(priceModel.getAnnualDrift(), priceModel.getAnnualVol(), initialPrice, budgetAtT, months);
        MonteCarloEngine.Result cached = MC_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        long t0 = System.nanoTime();
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                priceModel.getAnnualDrift(), priceModel.getAnnualVol(),
                initialPrice, budgetAtT,
                months, MonteCarloEngine.DEFAULT_SIMULATIONS, MonteCarloEngine.DEFAULT_SEED);
        RecommendationProfiler.recordMc(System.nanoTime() - t0);

        if (MC_CACHE.size() >= MC_CACHE_MAX_SIZE) {
            MC_CACHE.clear();
        }
        MC_CACHE.put(key, result);
        return result;
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
