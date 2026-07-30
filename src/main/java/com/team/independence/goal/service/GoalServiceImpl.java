package com.team.independence.goal.service;

import com.team.independence.asset.service.AssetService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.property.service.RegionQueryService;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 프론트 희망조건 입력을 검증·정규화하고 region_code까지 붙여 돌려준다.
 * 순자산 + 월저축액을 연 5% 복리로 굴린 예상값으로 budget까지 계산해 반환.
 * 매물 중앙값 비교 / 목표 저장은 다음 단계에서 추가.
 */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private static final String TRADE_TYPE_JEONSE_EN = "JEONSE";
    private static final String TRADE_TYPE_JEONSE_KR = "전세";
    private static final String TRADE_TYPE_WOLSE_KR = "월세";

    /** 예산 계산에 적용하는 연 이자율(고정 상수). 실제 상품 금리 연동 없이 5%로 가정. */
    private static final double ANNUAL_INTEREST_RATE = 0.05;

    private final RegionQueryService regionQueryService;
    private final AssetService assetService;

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

        long netAssets = assetService.getNetAssets(memberId);
        long months = monthsUntil(request.getTargetDate());
        long projectedNetAssets = calculateProjectedNetAssets(netAssets, months);
        long projectedSavings = calculateProjectedSavings(request.getMonthlySavings(), months);
        long totalBudget = projectedNetAssets + projectedSavings;

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
                        .recognizedAssets(projectedNetAssets)
                        .projectedSavings(projectedSavings)
                        .build())
                .build();
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

    /** 지금 가진 순자산이 목표시점까지 복리로 불어난 값(거치식) */
    private long calculateProjectedNetAssets(long netAssets, long months) {
        if (months == 0) {
            return netAssets;
        }
        double monthlyRate = monthlyInterestRate();
        return Math.round(netAssets * Math.pow(1 + monthlyRate, months));
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
