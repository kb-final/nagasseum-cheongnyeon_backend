package com.team.independence.goal.service.calculator;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
     * 월 복리 이율. 상수식이라 호출마다 다시 구할 이유가 없다.
     *
     * <p>도달 개월 수 탐색은 최대 1200번 반복하고 그 안에서 다시 이율을 쓴다.
     * 매번 {@code Math.pow}를 부르면 같은 값을 수천 번 계산한다.
     */
    private static final double MONTHLY_INTEREST_RATE =
            Math.pow(1 + ANNUAL_INTEREST_RATE, 1.0 / 12) - 1;

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

    /**
     * n개월 후 예상 총 예산을 계산한다 — 대출 스케줄 반영 버전.
     *
     * <p>매월 아직 상환 중인 대출(remainingMonths >= m)의 월 원리금 합계를 rawSaving에서 차감한 뒤
     * 복리로 누적한다. 대출이 끝난 달부터는 rawSaving 전액이 저축된다.
     */
    public long calculate(AssetNetWorthBreakdown netWorth, long rawSaving,
                          List<LoanSchedule> loanSchedules, long months) {
        double r = MONTHLY_INTEREST_RATE;
        ActiveLoanPayments active = new ActiveLoanPayments(loanSchedules);
        long cumulativeSavings = 0L;
        for (long m = 1; m <= months; m++) {
            long effectiveThisMonth = Math.max(0, rawSaving - active.at(m));
            cumulativeSavings = Math.round(cumulativeSavings * (1 + r)) + effectiveThisMonth;
        }
        return calculateGrownAmount(netWorth.getInterestBearingAssets(), months)
                + netWorth.getFlatRecognizedAssets()
                + cumulativeSavings;
    }

    /**
     * 예산이 목표 금액에 도달하는 최소 개월수를 탐색한다 — 대출 스케줄 반영 버전.
     *
     * <p>매월 활성 대출 상환액만 차감 후 복리 누적. 대출 소멸 이후에는 rawSaving 전액 반영.
     */
    public Long monthsToReach(AssetNetWorthBreakdown netWorth, long rawSaving,
                              List<LoanSchedule> loanSchedules, long targetAmount) {
        long growingAssets = netWorth.getInterestBearingAssets();
        long fixedAssets   = netWorth.getFlatRecognizedAssets();

        if (growingAssets + fixedAssets >= targetAmount) return 0L;

        double r = MONTHLY_INTEREST_RATE;
        ActiveLoanPayments active = new ActiveLoanPayments(loanSchedules);
        long cumulativeSavings = 0L;
        for (long m = 1; m <= MAX_FORECAST_MONTHS; m++) {
            long effectiveThisMonth = Math.max(0, rawSaving - active.at(m));
            cumulativeSavings = Math.round(cumulativeSavings * (1 + r)) + effectiveThisMonth;

            long budget = calculateGrownAmount(growingAssets, m) + fixedAssets + cumulativeSavings;
            if (budget >= targetAmount) return m;
        }
        return null;
    }

    /**
     * 달성률(%). 0~100으로 자른다.
     * goal_snapshot 집계·홈 요약·목표 상세가 동일한 규칙을 써야 화면 간 수치가 일치한다.
     */
    public static Double calculateAchievementRate(long currentAmount, long targetAmount) {
        if (targetAmount <= 0) {
            return 0.0;
        }
        double rate = currentAmount * 100.0 / targetAmount;
        double clamped = Math.min(100.0, Math.max(0.0, rate));
        return Math.round(clamped * 100) / 100.0;
    }

    private double monthlyInterestRate() {
        return MONTHLY_INTEREST_RATE;
    }

    /**
     * 개월 수가 커질수록 만기된 대출이 빠지는 월 원리금 합계를 순차적으로 내준다.
     *
     * <p>{@code m}개월차에 아직 상환 중인 대출은 {@code remainingMonths >= m}인 것들이다.
     * 매달 전체 스케줄을 스트림으로 다시 훑으면 도달 시점 탐색(최대 1200개월)에서만
     * 스트림 파이프라인이 1200번 만들어진다. 잔여 기간 오름차순으로 한 번 정렬해 두면
     * 만기가 지난 대출을 커서로 하나씩 빼면서 O(대출수 log 대출수 + 개월수)로 끝난다.
     *
     * <p>월을 1부터 단조 증가로만 조회한다고 가정한다(두 루프 모두 m=1부터 1씩 증가).
     */
    private static final class ActiveLoanPayments {

        private final long[] remainingMonths;
        private final long[] payments;
        private int expiredCount;
        private long activeSum;

        private ActiveLoanPayments(List<LoanSchedule> loanSchedules) {
            List<LoanSchedule> sorted = new ArrayList<>(loanSchedules);
            sorted.sort(Comparator.comparingLong(LoanSchedule::remainingMonths));

            remainingMonths = new long[sorted.size()];
            payments = new long[sorted.size()];
            long sum = 0L;
            for (int i = 0; i < sorted.size(); i++) {
                remainingMonths[i] = sorted.get(i).remainingMonths();
                payments[i] = sorted.get(i).monthlyPayment();
                sum += payments[i];
            }
            this.activeSum = sum;
        }

        /** {@code month}개월차에 아직 상환 중인 대출의 월 원리금 합계 */
        private long at(long month) {
            while (expiredCount < remainingMonths.length && remainingMonths[expiredCount] < month) {
                activeSum -= payments[expiredCount];
                expiredCount++;
            }
            return activeSum;
        }
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
