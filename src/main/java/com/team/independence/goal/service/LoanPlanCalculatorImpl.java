package com.team.independence.goal.service;

import com.team.independence.asset.dto.account.LoanAccountDetailItem;
import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.asset.service.LoanAccountService;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.member.service.MemberService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** {@link LoanPlanCalculator} 구현체. 계산 가정은 인터페이스 주석 참고. */
@Service
@RequiredArgsConstructor
public class LoanPlanCalculatorImpl implements LoanPlanCalculator {

    private static final double DSR_LIMIT               = 0.40;
    // 실제 대출 조건을 알 수 없으므로 신규·기존 대출 모두 동일한 금리로 추정한다
    private static final double ASSUMED_LOAN_ANNUAL_RATE = 0.035;
    private static final int    NEW_LOAN_TERM_MONTHS     = 360;

    private final MemberService       memberService;
    private final LoanAccountService  loanAccountService;
    private final AssetSummaryService assetSummaryService;
    private final BudgetCalculator    budgetCalculator;

    @Override
    public LoanPlans calculate(long memberId, long requiredAmount, YearMonth targetDate) {
        long months = ChronoUnit.MONTHS.between(YearMonth.now(), targetDate);
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);

        long loanXSaving = calcMonthlySavingNeeded(netWorth, requiredAmount, months);
        GoalRecommendationResponse.LoanXPlan loanX = GoalRecommendationResponse.LoanXPlan.builder()
                .targetAmount(requiredAmount)
                .targetDate(targetDate)
                .monthlySaving(loanXSaving)
                .build();

        long loanAmount = Math.min(calcMaxLoanAmount(memberId), requiredAmount);
        if (loanAmount <= 0) {
            return LoanPlans.builder().loanX(loanX).loanO(null).build();
        }

        long selfFunded = requiredAmount - loanAmount;
        long loanOSaving = calcMonthlySavingNeeded(netWorth, selfFunded, months);
        GoalRecommendationResponse.LoanOPlan loanO = GoalRecommendationResponse.LoanOPlan.builder()
                .loanAmount(loanAmount)
                .targetAmount(selfFunded)
                .targetDate(targetDate)
                .monthlySaving(loanOSaving)
                .build();

        return LoanPlans.builder().loanX(loanX).loanO(loanO).build();
    }

    @Override
    public long calcMaxLoanAmount(long memberId) {
        Long monthlyIncome = memberService.getMember(memberId).monthlyIncome();
        if (monthlyIncome == null || monthlyIncome <= 0) return 0L;

        double r = ASSUMED_LOAN_ANNUAL_RATE / 12.0;
        double factor = Math.pow(1 + r, NEW_LOAN_TERM_MONTHS);

        double existingMonthlyPayments = loanAccountService.getLoanAccounts(memberId).stream()
                .mapToDouble(loan -> calcExistingMonthlyPayment(loan, r))
                .sum();

        double availableMonthly = monthlyIncome * DSR_LIMIT - existingMonthlyPayments;
        if (availableMonthly <= 0) return 0L;

        // 연금 현가: M × (factor − 1) / (r × factor)
        return (long) (availableMonthly * (factor - 1) / (r * factor));
    }

    // 기존 대출 한 건의 월 원리금 상환액. endDate 기준 잔여 기간으로 역산.
    // 실제 대출 조건을 사용할 수 없으므로 ASSUMED_LOAN_ANNUAL_RATE, 원리금균등상환으로 추정한다.
    private double calcExistingMonthlyPayment(LoanAccountDetailItem loan, double r) {
        if (loan.getLoanBalance() == null || loan.getLoanBalance() <= 0) return 0.0;
        if (loan.getEndDate() == null) return 0.0;
        long remaining = ChronoUnit.MONTHS.between(LocalDate.now(), loan.getEndDate());
        if (remaining <= 0) return 0.0;
        double f = Math.pow(1 + r, remaining);
        return loan.getLoanBalance() * r * f / (f - 1);
    }

    // n개월 안에 targetAmount에 도달하기 위한 최소 월 저축액을 이진탐색으로 역산.
    private long calcMonthlySavingNeeded(AssetNetWorthBreakdown netWorth, long targetAmount, long months) {
        if (targetAmount <= 0) return 0L;
        if (months <= 0) return targetAmount;
        if (budgetCalculator.calculate(netWorth, 0L, months) >= targetAmount) return 0L;

        long lo = 0L;
        long hi = targetAmount;
        while (hi - lo > 1) {
            long mid = (lo + hi) / 2;
            if (budgetCalculator.calculate(netWorth, mid, months) >= targetAmount) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }
}
