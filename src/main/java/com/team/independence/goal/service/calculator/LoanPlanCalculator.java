package com.team.independence.goal.service.calculator;

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
import org.springframework.stereotype.Component;

/**
 * 모든 추천 알고리즘이 공유하는 계산기.
 *
 * <p>계산 가정
 * <ul>
 *   <li>신규 대출: DSR 40% 한도, 연 3.5% 30년 원리금균등상환</li>
 *   <li>기존 대출 월 상환액: 잔액과 {@code loan_account.end_date} 잔여 기간으로 같은 금리(연 3.5%)로 역산.
 *       {@code end_date}가 null이거나 이미 만기된 대출은 상환 부담 없음으로 처리</li>
 *   <li>저축: 연 5% 복리({@link BudgetCalculator}에 위임)</li>
 *   <li>소득은 {@code member.monthly_income}, 보유 자산은 순자산 분해 결과를 사용한다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LoanPlanCalculator {

    private static final double DSR_LIMIT                = 0.40;
    // TODO: 실제 대출 조건을 알 수 없으므로 신규·기존 대출 모두 동일한 금리로 추정한다
    // TODO: 추후 CODEF API 추가 사용을 통해 각 대출 계좌의 이율을 받아올 수 있다
    private static final double ASSUMED_LOAN_ANNUAL_RATE = 0.035;
    private static final int    NEW_LOAN_TERM_MONTHS     = 360;

    private final MemberService       memberService;
    private final LoanAccountService  loanAccountService;
    private final AssetSummaryService assetSummaryService;
    private final BudgetCalculator    budgetCalculator;

    /**
     * 목표 금액과 목표 시점으로 대출 없는 플랜과 대출 낀 플랜을 함께 계산한다.
     *
     * <p>DSR 한도가 0이면(소득 미등록·기존 대출 과다) loanO는 null이 된다.
     * 대출 한도가 목표 금액을 초과하는 경우 대출액은 목표 금액으로 캡되며,
     * 자력 부담과 월 저축액은 0이 된다.
     */
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

    /**
     * 기존 대출 계좌 전체의 월 원리금 합계를 반환한다.
     *
     * <p>end_date가 없거나 이미 만기된 대출은 상환 부담 없음으로 처리한다.
     * 대출이 없거나 잔액이 0이면 0을 반환한다.
     */
    public long calcTotalExistingMonthlyPayment(long memberId) {
        double r = ASSUMED_LOAN_ANNUAL_RATE / 12.0;
        return Math.round(loanAccountService.getLoanAccounts(memberId).stream()
                .mapToDouble(loan -> calcExistingMonthlyPayment(loan, r))
                .sum());
    }

    /**
     * DSR 40% 기준 신규 대출 최대 가능액을 계산한다.
     *
     * <p>공식: (월소득 × 0.4 − 기존 대출 월 원리금 합계)를 30년 연금 현가로 환산.
     * 소득 미등록이거나 기존 대출이 DSR 40%를 이미 소진한 경우 0을 반환한다.
     */
    public long calcMaxLoanAmount(long memberId) {
        Long monthlyIncome = memberService.getMember(memberId).monthlyIncome();
        if (monthlyIncome == null || monthlyIncome <= 0) return 0L;

        double r = ASSUMED_LOAN_ANNUAL_RATE / 12.0;
        double factor = Math.pow(1 + r, NEW_LOAN_TERM_MONTHS);

        long existingMonthlyPayment = calcTotalExistingMonthlyPayment(memberId);
        double availableMonthly = monthlyIncome * DSR_LIMIT - existingMonthlyPayment;
        if (availableMonthly <= 0) return 0L;

        // 연금 현가: M × (factor − 1) / (r × factor)
        return (long) (availableMonthly * (factor - 1) / (r * factor));
    }

    // 기존 대출 한 건의 월 원리금 상환액. endDate 기준 잔여 기간으로 역산.
    // TODO: 실제 대출 조건을 사용할 수 없으므로 ASSUMED_LOAN_ANNUAL_RATE, 원리금균등상환으로 추정한다.
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
