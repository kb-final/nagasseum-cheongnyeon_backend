package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import org.springframework.stereotype.Service;

/**
 * GoalServiceImpl의 예산 계산 공식을 그대로 옮긴 구현체.
 *
 * <p>GoalServiceImpl.ANNUAL_INTEREST_RATE(0.05)와 MAX_FORECAST_MONTHS(1200)를
 * 동일하게 유지한다. 상수가 바뀌면 두 곳을 같이 바꿔야 한다.
 * GoalServiceImpl가 이 클래스를 참조하도록 전환되면 이 주석을 제거한다.
 */
@Service
public class BudgetCalculatorImpl implements BudgetCalculator {

    private static final double ANNUAL_INTEREST_RATE = 0.05;
    private static final long MAX_FORECAST_MONTHS = 1200;

    @Override
    public long calculate(AssetNetWorthBreakdown netWorth, long monthlySaving, long months) {
        return calculateGrownAmount(netWorth.getInterestBearingAssets(), months)
                + netWorth.getFlatRecognizedAssets()
                + calculateProjectedSavings(monthlySaving, months);
    }

    @Override
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
