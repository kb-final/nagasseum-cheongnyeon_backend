package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.service.RecommendationAlgorithm.MemberFinancialContext;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.property.mapper.RegionMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록된 추천 알고리즘을 모두 실행해 결과를 모은다.
 *
 * <p>추천 로직은 전혀 갖고 있지 않다. 알고리즘 추가·제거 시 이 클래스는 수정하지 않는다.
 *
 * <p>알고리즘 목록을 {@link ObjectProvider}로 받는 이유는, 아직 구현체가 하나도 없는 상태에서도
 * 애플리케이션이 기동되도록 하기 위해서다. {@code List<RecommendationAlgorithm>}를 생성자로 직접
 * 주입하면 후보 빈이 없을 때 기동이 실패해, 알고리즘 담당자들이 서로의 구현을 기다려야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalRecommendationServiceImpl implements GoalRecommendationService {

    private final AssetConnectionService assetConnectionService;
    private final AssetSummaryService assetSummaryService;
    private final LoanPlanCalculator loanPlanCalculator;
    private final RegionMapper regionMapper;
    private final ObjectProvider<RecommendationAlgorithm> algorithmProvider;
    private final GoalRecommendationStore recommendationStore;
    @Qualifier("algorithmExecutor")
    private final Executor algorithmExecutor;

    @Override
    @Transactional(readOnly = true)
    public GoalRecommendationResponse recommend(long memberId, GoalRecommendationRequest request) {
        // 자산이 연동돼 있어야 예산 계산이 가능하다
        assetConnectionService.validateConnectedAccountExists(memberId);

        List<RecommendationAlgorithm> algorithms = algorithmProvider.orderedStream()
                .collect(Collectors.toList());

        if (algorithms.isEmpty()) {
            log.warn("등록된 추천 알고리즘이 없습니다. RecommendationAlgorithm 구현체를 추가해야 합니다.");
        }

        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long monthlySaving = assetSummaryService.getMonthlySavingsOrZero(memberId);
        long loanPayment   = loanPlanCalculator.calcTotalExistingMonthlyPayment(memberId);
        MemberFinancialContext ctx = new MemberFinancialContext(netWorth, monthlySaving, loanPayment);

        long currentAvailableAmount = netWorth.getInterestBearingAssets()
                + netWorth.getFlatRecognizedAssets();

        List<CompletableFuture<Optional<GoalRecommendationResponse.RecommendationItem>>> futures =
                algorithms.stream()
                        .map(algorithm -> CompletableFuture.supplyAsync(
                                () -> runSafely(algorithm, memberId, request, ctx),
                                algorithmExecutor))
                        .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<GoalRecommendationResponse.RecommendationItem> recommendations = futures.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (recommendations.isEmpty()) {
            throw new BusinessException(ErrorCode.GOAL_RECOMMENDATION_NO_CANDIDATE);
        }

        GoalRecommendationResponse response = GoalRecommendationResponse.builder()
                .originalPreference(buildOriginalPreference(request, monthlySaving))
                .financialContext(GoalRecommendationResponse.FinancialContext.builder()
                        .currentAvailableAmount(currentAvailableAmount)
                        .build())
                .recommendations(recommendations)
                .build();

        // 결과 화면 재진입 시 재계산 없이 그대로 돌려주기 위해 저장한다
        recommendationStore.save(memberId, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public GoalRecommendationResponse getSavedRecommendation(long memberId) {
        return recommendationStore.find(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOAL_RECOMMENDATION_NOT_FOUND));
    }

    private GoalRecommendationResponse.OriginalPreference buildOriginalPreference(
            GoalRecommendationRequest request, long monthlySaving) {

        String regionCode = request.getRegionCode();
        String regionName = regionCode.length() == 2
                ? regionMapper.findSidoNameByPrefix(regionCode)
                : regionMapper.findFullNameByCode(regionCode);

        GoalRecommendationResponse.OriginalCondition condition =
                GoalRecommendationResponse.OriginalCondition.builder()
                        .regionCode(regionCode)
                        .regionName(regionName)
                        .housingType(request.getPropertyType())
                        .dealType(request.getTradeType())
                        .areaMin(request.getSizeMin())
                        .areaMax(request.getSizeMax())
                        .depositMin(request.getDepositMin())
                        .depositMax(request.getDepositMax())
                        .monthlyRentMin(request.getMonthlyRentMin())
                        .monthlyRentMax(request.getMonthlyRentMax())
                        .build();

        return GoalRecommendationResponse.OriginalPreference.builder()
                .condition(condition)
                .targetDate(request.getTargetDate())
                .monthlySaving(monthlySaving)
                .build();
    }

    /**
     * 알고리즘 하나가 실패해도 나머지 추천은 내려주기 위해 예외를 삼킨다.
     *
     * <p>알고리즘들은 서로 독립적이라 하나의 오류가 전체 응답을 막을 이유가 없다.
     * 다만 모든 알고리즘이 실패하면 결과가 비어 {@code GOAL_RECOMMENDATION_NO_CANDIDATE}로 이어진다.
     */
    private Optional<GoalRecommendationResponse.RecommendationItem> runSafely(
            RecommendationAlgorithm algorithm, long memberId,
            GoalRecommendationRequest request, MemberFinancialContext ctx) {
        try {
            return algorithm.recommend(memberId, request, ctx);
        } catch (Exception e) {
            log.error("추천 알고리즘 실행 실패. algorithm={}, memberId={}",
                    algorithm.getClass().getSimpleName(), memberId, e);
            return Optional.empty();
        }
    }

}
