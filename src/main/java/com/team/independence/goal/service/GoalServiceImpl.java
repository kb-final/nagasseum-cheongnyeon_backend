package com.team.independence.goal.service;

import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.property.dto.RentMarketStatsResponse;
import com.team.independence.property.service.RegionQueryService;
import com.team.independence.property.service.RentMarketQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 프론트 희망조건 입력을 검증·정규화하고 region_code까지 붙여 돌려준다.
 * 순자산 중 예적금만 연 5% 복리로 굴리고(나머지는 인정률만 반영한 원금 그대로) + 월저축액 예상값으로
 * budget을, 조건에 맞는 실거래 보증금 백분위수를 marketStats로 계산해 반환.
 * budget과 median을 비교해 status/shortfall을 매기고, 부족(INSUFFICIENT)할 때
 * 저축액 증가/기간 연장/평수 축소 3가지 조정 제안을 함께 계산한다.
 * 목표 저장은 다음 단계에서 추가.
 */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private static final String TRADE_TYPE_JEONSE_EN = "JEONSE";
    private static final String TRADE_TYPE_JEONSE_KR = "전세";
    private static final String TRADE_TYPE_WOLSE_KR = "월세";

    private static final String STATUS_ACHIEVABLE = "ACHIEVABLE";
    private static final String STATUS_INSUFFICIENT = "INSUFFICIENT";
    private static final String STATUS_NO_DATA = "NO_DATA";

    /** 예산 계산에 적용하는 연 이자율(고정 상수). 실제 상품 금리 연동 없이 5%로 가정. */
    private static final double ANNUAL_INTEREST_RATE = 0.05;

    /** 평 → ㎡ 변환 계수 */
    private static final BigDecimal PYEONG_TO_SQM = BigDecimal.valueOf(3.3058);

    /** 기간 연장 제안 탐색 상한(개월) */
    private static final long EXTEND_PERIOD_MAX_MONTHS = 240;
    /** 평수 축소 제안 탐색 상한(평) */
    private static final int REDUCE_SIZE_MAX_STEPS = 10;

    private final RegionQueryService regionQueryService;
    private final AssetService assetService;
    private final RentMarketQueryService rentMarketQueryService;

    @Override
    public GoalDiagnosisResponse diagnose(Long memberId, GoalDiagnosisRequest request) {
        validateRange(request.getSizeMin(), request.getSizeMax());
        validateRange(request.getDepositMin(), request.getDepositMax());

        String tradeTypeKr = toKoreanTradeType(request.getTradeType());

        long monthlyRentMin = normalizeMonthlyRent(tradeTypeKr, request.getMonthlyRentMin());
        long monthlyRentMax = normalizeMonthlyRent(tradeTypeKr, request.getMonthlyRentMax());
        if (!TRADE_TYPE_JEONSE_KR.equals(tradeTypeKr)) {
            validateRange(monthlyRentMin, monthlyRentMax);
        }

        String regionCode = regionQueryService.resolveRegionCode(
                request.getRegion().getSido(), request.getRegion().getSigungu());

        AssetNetWorthBreakdown netWorth = assetService.getNetWorthBreakdown(memberId);
        long interestBearingAssets = netWorth.getInterestBearingAssets();
        long flatRecognizedAssets = netWorth.getFlatRecognizedAssets();

        long months = monthsUntil(request.getTargetDate());
        long recognizedAssets = calculateGrownAmount(interestBearingAssets, months) + flatRecognizedAssets;
        long projectedSavings = calculateProjectedSavings(request.getMonthlySavings(), months);
        long totalBudget = recognizedAssets + projectedSavings;

        RentMarketStatsResponse marketStats = rentMarketQueryService.getMarketStats(
                regionCode, request.getPropertyType(), tradeTypeKr,
                toSqm(request.getSizeMin()), toSqm(request.getSizeMax()));

        String status = determineStatus(marketStats, totalBudget);
        Long shortfall = STATUS_INSUFFICIENT.equals(status)
                ? marketStats.getMedian() - totalBudget
                : null;

        GoalDiagnosisResponse.AdjustmentSuggestions adjustmentSuggestions = null;
        if (STATUS_INSUFFICIENT.equals(status)) {
            adjustmentSuggestions = GoalDiagnosisResponse.AdjustmentSuggestions.builder()
                    .increaseSavings(calculateIncreaseSavingsSuggestion(
                            marketStats.getMedian(), recognizedAssets, request.getMonthlySavings(), months))
                    .extendPeriod(calculateExtendPeriodSuggestion(
                            interestBearingAssets, flatRecognizedAssets, request.getMonthlySavings(), months,
                            marketStats.getMedian(), request.getTargetDate()))
                    .reduceSize(calculateReduceSizeSuggestion(
                            regionCode, request.getPropertyType(), tradeTypeKr,
                            request.getSizeMin(), request.getSizeMax(), totalBudget))
                    .build();
        }

        return GoalDiagnosisResponse.builder()
                .regionCode(regionCode)
                .region(GoalDiagnosisResponse.RegionInfo.builder()
                        .sido(request.getRegion().getSido())
                        .sigungu(request.getRegion().getSigungu())
                        .build())
                .propertyType(request.getPropertyType())
                .tradeType(request.getTradeType())
                .sizeMin(request.getSizeMin())
                .sizeMax(request.getSizeMax())
                .depositMin(request.getDepositMin())
                .depositMax(request.getDepositMax())
                .monthlyRentMin(monthlyRentMin)
                .monthlyRentMax(monthlyRentMax)
                .monthlySavings(request.getMonthlySavings())
                .targetDate(request.getTargetDate())
                .budget(GoalDiagnosisResponse.BudgetResult.builder()
                        .totalBudget(totalBudget)
                        .recognizedAssets(recognizedAssets)
                        .projectedSavings(projectedSavings)
                        .build())
                .marketStats(GoalDiagnosisResponse.MarketStats.builder()
                        .p25(marketStats.getP25())
                        .median(marketStats.getMedian())
                        .p75(marketStats.getP75())
                        .sampleCount(marketStats.getSampleCount())
                        .build())
                .status(status)
                .shortfall(shortfall)
                .adjustmentSuggestions(adjustmentSuggestions)
                .build();
    }

    /** 같은 개월수 기준, budget이 median에 도달하도록 월저축액을 역산 */
    private GoalDiagnosisResponse.IncreaseSavingsSuggestion calculateIncreaseSavingsSuggestion(
            long median, long recognizedAssets, long monthlySavings, long months) {
        if (months == 0) {
            return null;
        }
        double monthlyRate = monthlyInterestRate();
        double annuityFactor = (Math.pow(1 + monthlyRate, months) - 1) / monthlyRate;
        long requiredSavings = median - recognizedAssets;
        long adjustedMonthlySavings = Math.round(requiredSavings / annuityFactor);

        return GoalDiagnosisResponse.IncreaseSavingsSuggestion.builder()
                .additionalMonthlySavings(adjustedMonthlySavings - monthlySavings)
                .adjustedMonthlySavings(adjustedMonthlySavings)
                .build();
    }

    /** 월저축액 고정, budget이 median에 도달하는 최소 개월수를 탐색(최대 EXTEND_PERIOD_MAX_MONTHS).
     *  interestBearingAssets(예적금)만 개월수에 따라 다시 복리 성장시키고, flatRecognizedAssets는 그대로 더한다. */
    private GoalDiagnosisResponse.ExtendPeriodSuggestion calculateExtendPeriodSuggestion(
            long interestBearingAssets, long flatRecognizedAssets, long monthlySavings, long months,
            long median, YearMonth targetDate) {
        for (long n = months + 1; n <= EXTEND_PERIOD_MAX_MONTHS; n++) {
            long recognizedAssetsAtN = calculateGrownAmount(interestBearingAssets, n) + flatRecognizedAssets;
            long projected = recognizedAssetsAtN + calculateProjectedSavings(monthlySavings, n);
            if (projected >= median) {
                long additionalMonths = n - months;
                return GoalDiagnosisResponse.ExtendPeriodSuggestion.builder()
                        .additionalMonths(additionalMonths)
                        .adjustedTargetDate(targetDate.plusMonths(additionalMonths))
                        .build();
            }
        }
        return null;
    }

    /** sizeMin은 고정, sizeMax만 1평씩 줄여가며 median이 budget 이내로 들어오는 첫 지점을 탐색(최대 REDUCE_SIZE_MAX_STEPS평) */
    private GoalDiagnosisResponse.ReduceSizeSuggestion calculateReduceSizeSuggestion(
            String regionCode, String propertyType, String tradeTypeKr,
            int sizeMin, int sizeMax, long totalBudget) {
        int maxSteps = Math.min(REDUCE_SIZE_MAX_STEPS, sizeMax - sizeMin);
        for (int step = 1; step <= maxSteps; step++) {
            int candidateSizeMax = sizeMax - step;
            RentMarketStatsResponse stats = rentMarketQueryService.getMarketStats(
                    regionCode, propertyType, tradeTypeKr, toSqm(sizeMin), toSqm(candidateSizeMax));
            if (stats.getSampleCount() > 0 && stats.getMedian() <= totalBudget) {
                return GoalDiagnosisResponse.ReduceSizeSuggestion.builder()
                        .deltaSizeMax(candidateSizeMax - sizeMax)
                        .newSizeMax(candidateSizeMax)
                        .build();
            }
        }
        return null;
    }

    /** 데이터가 없으면 NO_DATA, budget이 중앙값 이상이면 ACHIEVABLE, 아니면 INSUFFICIENT */
    private String determineStatus(RentMarketStatsResponse marketStats, long totalBudget) {
        if (marketStats.getSampleCount() == 0) {
            return STATUS_NO_DATA;
        }
        return totalBudget >= marketStats.getMedian() ? STATUS_ACHIEVABLE : STATUS_INSUFFICIENT;
    }

    /** 평(pyeong) → ㎡ 변환 */
    private BigDecimal toSqm(Integer pyeong) {
        return BigDecimal.valueOf(pyeong).multiply(PYEONG_TO_SQM).setScale(2, RoundingMode.HALF_UP);
    }

    private String toKoreanTradeType(String tradeTypeEn) {
        if (TRADE_TYPE_JEONSE_EN.equals(tradeTypeEn)) {
            return TRADE_TYPE_JEONSE_KR;
        }
        return TRADE_TYPE_WOLSE_KR;
    }

    /** 목표시점까지 남은 개월수. 이미 지난 달이면 0으로 clamp. */
    private long monthsUntil(YearMonth targetDate) {
        long months = YearMonth.now().until(targetDate, java.time.temporal.ChronoUnit.MONTHS);
        return Math.max(months, 0);
    }

    /** 연 이자율을 복리 기준 월 이자율로 환산: (1+연이자율)^(1/12) - 1 */
    private double monthlyInterestRate() {
        return Math.pow(1 + ANNUAL_INTEREST_RATE, 1.0 / 12) - 1;
    }

    /** 원금이 목표시점까지 복리로 불어난 값(거치식). 예적금(interestBearingAssets)에만 적용한다. */
    private long calculateGrownAmount(long principal, long months) {
        if (months == 0) {
            return principal;
        }
        double monthlyRate = monthlyInterestRate();
        return Math.round(principal * Math.pow(1 + monthlyRate, months));
    }

    /** 매달 monthlySavings씩 넣는 적금이 목표시점까지 복리로 불어난 값(적립식 미래가치) */
    private long calculateProjectedSavings(long monthlySavings, long months) {
        if (months == 0) {
            return 0L;
        }
        double monthlyRate = monthlyInterestRate();
        double futureValueFactor = (Math.pow(1 + monthlyRate, months) - 1) / monthlyRate;
        return Math.round(monthlySavings * futureValueFactor);
    }

    /** 전세면 월세 입력값과 무관하게 0으로 고정 */
    private long normalizeMonthlyRent(String tradeTypeKr, Long monthlyRent) {
        if (TRADE_TYPE_JEONSE_KR.equals(tradeTypeKr)) {
            return 0L;
        }
        return monthlyRent != null ? monthlyRent : 0L;
    }

    private void validateRange(int min, int max) {
        if (min > max) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_CONDITION);
        }
    }

    private void validateRange(long min, long max) {
        if (min > max) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_CONDITION);
        }
    }
}
