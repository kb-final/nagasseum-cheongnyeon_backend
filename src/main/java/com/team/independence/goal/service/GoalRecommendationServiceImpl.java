package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.service.RecommendationAlgorithm.MemberFinancialContext;
import com.team.independence.goal.service.algorithm.HoldOutAlgorithm;
import com.team.independence.goal.service.algorithm.RealisticAlgorithm;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.goal.service.calculator.LoanSchedule;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.service.RentMedianService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 등록된 추천 알고리즘을 2-Phase로 실행해 결과를 모은다.
 *
 * <h3>실행 순서</h3>
 * <ul>
 *   <li><b>Phase 1 (병렬)</b>: RealisticAlgorithm + 기타 알고리즘(PreferenceAlgorithm 등)</li>
 *   <li><b>Phase 2 (Realistic 완료 후)</b>: HoldOutAlgorithm — Realistic 결과를 기준점으로 사용</li>
 * </ul>
 *
 * <p>HoldOut은 Realistic 결과를 기준점으로 삼아 동작하므로 Realistic 완료를 기다렸다가 실행한다.
 * 다른 알고리즘은 HoldOut 완료와 무관하게 Phase 1에서 병렬로 처리한다.
 *
 * <p>알고리즘 하나가 실패해도 나머지 추천은 내려준다.
 * 모든 알고리즘이 실패하면 {@code GOAL_RECOMMENDATION_NO_CANDIDATE}를 던진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalRecommendationServiceImpl implements GoalRecommendationService {

    private final AssetConnectionService assetConnectionService;
    private final AssetSummaryService assetSummaryService;
    private final LoanPlanCalculator loanPlanCalculator;
    private final RegionMapper regionMapper;
    private final RentMedianService rentMedianService;
    private final RealisticAlgorithm realisticAlgorithm;
    private final HoldOutAlgorithm holdOutAlgorithm;
    /** Preference 등 Phase 1 나머지 알고리즘 — HoldOut·Realistic은 위에서 직접 주입한다. */
    private final ObjectProvider<RecommendationAlgorithm> algorithmProvider;
    private final GoalRecommendationStore recommendationStore;
    @Qualifier("algorithmExecutor")
    private final Executor algorithmExecutor;

    /**
     * {@code @Transactional}을 붙이지 않는다.
     *
     * <p>실제 DB 작업은 대부분 algorithmExecutor 스레드에서 일어나는데, 그 스레드들은 요청 스레드의
     * 트랜잭션을 상속받지 못하고 각자 커넥션을 잡는다. 여기에 트랜잭션을 걸면 요청 스레드가
     * join()으로 대기하는 내내 커넥션 하나를 쓰지도 않으면서 붙잡고 있게 되어, 동시 요청 수만큼
     * 커넥션 풀이 먼저 마른다. 개별 조회는 각 서비스의 readOnly 트랜잭션이 이미 감싸고 있다.
     */
    @Override
    public GoalRecommendationResponse recommend(long memberId, GoalRecommendationRequest request) {
        assetConnectionService.validateConnectedAccountExists(memberId);

        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long monthlySaving = request.getMonthlySavings();
        List<LoanSchedule> loanSchedules = loanPlanCalculator.getLoanSchedules(memberId);
        MemberFinancialContext ctx = new MemberFinancialContext(netWorth, monthlySaving, loanSchedules);

        // Phase 1: Realistic + 기타(Preference 등) 병렬 실행
        CompletableFuture<List<GoalRecommendationResponse.RecommendationItem>> realisticFuture =
                CompletableFuture.supplyAsync(
                        () -> runSafely(realisticAlgorithm, memberId, request, ctx), algorithmExecutor);

        List<CompletableFuture<List<GoalRecommendationResponse.RecommendationItem>>> otherFutures =
                algorithmProvider.orderedStream()
                        .filter(a -> !(a instanceof RealisticAlgorithm) && !(a instanceof HoldOutAlgorithm))
                        .map(a -> CompletableFuture.supplyAsync(
                                () -> runSafely(a, memberId, request, ctx), algorithmExecutor))
                        .collect(Collectors.toList());

        // Phase 2: Realistic 완료 후 HoldOut 실행
        CompletableFuture<List<GoalRecommendationResponse.RecommendationItem>> holdOutFuture =
                realisticFuture.thenApplyAsync(realisticItems -> {
                    GoalRecommendationResponse.RecommendationItem realisticItem =
                            realisticItems.isEmpty() ? null : realisticItems.get(0);
                    return runHoldOutSafely(memberId, request, ctx, realisticItem);
                }, algorithmExecutor);

        // 모두 완료 대기
        List<CompletableFuture<?>> allFutures = new ArrayList<>();
        allFutures.add(realisticFuture);
        allFutures.add(holdOutFuture);
        allFutures.addAll(otherFutures);
        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();

        // 결과 수집 (순서 고정: Realistic → HoldOut → 기타)
        List<GoalRecommendationResponse.RecommendationItem> recommendations = new ArrayList<>();
        recommendations.addAll(realisticFuture.join());
        recommendations.addAll(holdOutFuture.join());
        otherFutures.forEach(f -> recommendations.addAll(f.join()));

        if (recommendations.isEmpty()) {
            throw new BusinessException(ErrorCode.GOAL_RECOMMENDATION_NO_CANDIDATE);
        }

        GoalRecommendationResponse response = GoalRecommendationResponse.builder()
                .originalPreference(buildOriginalPreference(request, monthlySaving))
                .recommendations(recommendations)
                .build();

        recommendationStore.save(memberId, response);
        return response;
    }

    /** Redis만 읽으므로 트랜잭션이 필요 없다. 걸어 두면 쓰지 않을 커넥션을 잡는다. */
    @Override
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
                        .marketMedianAmount(resolveBaseMedianAmount(request))
                        .build();

        return GoalRecommendationResponse.OriginalPreference.builder()
                .condition(condition)
                .targetDate(request.getTargetDate())
                .monthlySaving(monthlySaving)
                .build();
    }

    /**
     * 원래 희망 조건의 실거래 중앙값을 조회한다.
     * 주거유형·거래유형·최소 평수가 하나라도 없으면 조회 불가이므로 null 반환.
     */
    private Long resolveBaseMedianAmount(GoalRecommendationRequest request) {
        if (request.getPropertyType() == null
                || request.getTradeType() == null
                || request.getSizeMin() == null) {
            return null;
        }
        try {
            RentMedianRequest medianReq = new RentMedianRequest();
            medianReq.setRegionCode(request.getRegionCode());
            medianReq.setHousingType(request.getPropertyType());
            medianReq.setDealType(request.getTradeType());
            medianReq.setAreaMin(request.getSizeMin());
            medianReq.setAreaMax(request.getSizeMax());
            medianReq.setDepositMin(0L);
            medianReq.setDepositMax(Long.MAX_VALUE);

            RentMedianResponse median = rentMedianService.getMedian(medianReq);
            return (median.getDeposit() != null) ? median.getDeposit().getQ3() : null;
        } catch (Exception e) {
            log.warn("[OriginalPreference] 기준 시세 조회 실패 — null 처리. regionCode={}", request.getRegionCode(), e);
            return null;
        }
    }

    /**
     * 알고리즘 하나가 실패해도 나머지 추천은 내려주기 위해 예외를 삼킨다.
     */
    private List<GoalRecommendationResponse.RecommendationItem> runSafely(
            RecommendationAlgorithm algorithm, long memberId,
            GoalRecommendationRequest request, MemberFinancialContext ctx) {
        try {
            return algorithm.recommend(memberId, request, ctx);
        } catch (Exception e) {
            log.error("추천 알고리즘 실행 실패. algorithm={}, memberId={}",
                    algorithm.getClass().getSimpleName(), memberId, e);
            return List.of();
        }
    }

    private List<GoalRecommendationResponse.RecommendationItem> runHoldOutSafely(
            long memberId, GoalRecommendationRequest request, MemberFinancialContext ctx,
            GoalRecommendationResponse.RecommendationItem realisticItem) {
        try {
            return holdOutAlgorithm.recommend(memberId, request, ctx, realisticItem);
        } catch (Exception e) {
            log.error("HoldOut 알고리즘 실행 실패. memberId={}", memberId, e);
            return List.of();
        }
    }
}
