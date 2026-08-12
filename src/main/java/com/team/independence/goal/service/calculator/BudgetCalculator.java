package com.team.independence.goal.service.calculator;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import org.springframework.stereotype.Component;

/**
 * n개월 후 예상 예산 계산.
 *
 * <p>계산 가정
 * <ul>
 *   <li>예적금(interestBearingAssets): 연 5% 복리 거치식 성장</li>
 *   <li>기타 자산(flatRecognizedAssets): 원금 그대로 인정</li>
 *   <li>월 저축: 연 5% 복리 적립식 미래가치 (ordinary annuity)</li>
 *   <li>월 저축액은 기존 대출 상환을 포함한 모든 지출 후의 순 적립액으로 가정</li>
 * </ul>
 */
@Component
public class BudgetCalculator {

    private static final double ANNUAL_INTEREST_RATE = 0.05;
    private static final long   MAX_FORECAST_MONTHS  = 1200;

    /**
     * n개월 후 예상 총 예산을 계산한다.
     *
     * @param netWorth      순자산 구성 (예적금 / 기타 자산)
     * @param monthlySaving 월 저축액 (원)
     * @param months        현재로부터 경과 개월수
     * @return 예상 총 예산 (원)
     */
    public long calculate(AssetNetWorthBreakdown netWorth, long monthlySaving, long months) {
        return calculateGrownAmount(netWorth.getInterestBearingAssets(), months)
                + netWorth.getFlatRecognizedAssets()
                + calculateProjectedSavings(monthlySaving, months);
    }

    /**
     * 예산이 목표 금액에 도달하는 최소 개월수를 탐색한다.
     *
     * @param netWorth      순자산 구성
     * @param monthlySaving 월 저축액 (원)
     * @param targetAmount  목표 금액 (원)
     * @return 도달 최소 개월수. 저축액이 0 이하이거나 상한 내에 도달하지 못하면 null
     */
    public Long monthsToReach(AssetNetWorthBreakdown netWorth, long monthlySaving, long targetAmount) {
        long growingAssets = netWorth.getInterestBearingAssets();
        long fixedAssets   = netWorth.getFlatRecognizedAssets();

        if (growingAssets + fixedAssets >= targetAmount) {
            return 0L;
        }
        if (monthlySaving <= 0) {
            return null;
        }

        for (long m = 1; m <= MAX_FORECAST_MONTHS; m++) {
            long budget = calculateGrownAmount(growingAssets, m) + fixedAssets
                    + calculateProjectedSavings(monthlySaving, m);
            if (budget >= targetAmount) {
                return m;
            }
        }
        return null;
    }

    private double monthlyInterestRate() {
        return Math.pow(1 + ANNUAL_INTEREST_RATE, 1.0 / 12) - 1;
    }

    private long calculateGrownAmount(long principal, long months) {
        if (months == 0) {
            return principal;
        }
        return Math.round(principal * Math.pow(1 + monthlyInterestRate(), months));
    }

    private long calculateProjectedSavings(long monthlySaving, long months) {
        if (months == 0) {
            return 0L;
        }
        double r = monthlyInterestRate();
        return Math.round(monthlySaving * (Math.pow(1 + r, months) - 1) / r);
    }
}
