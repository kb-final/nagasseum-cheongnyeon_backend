package com.team.independence.goal.service.calculator;

import com.team.independence.asset.dto.account.LoanAccountDetailItem;
import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.LoanAccountService;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.member.service.MemberService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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
    private final BudgetCalculator    budgetCalculator;

    /**
     * 목표 금액과 목표 시점으로 대출 없는 플랜과 대출 낀 플랜을 함께 계산한다.
     *
     * <p>DSR 한도가 0이면(소득 미등록·기존 대출 과다) loanO는 null이 된다.
     * 대출 한도가 목표 금액을 초과하는 경우 대출액은 목표 금액으로 캡되며,
     * 자력 부담과 월 저축액은 0이 된다.
     *
     * @param netWorth        호출자가 상위에서 한 번만 조회해 넘기는 순자산 구성.
     *                        여기서 다시 조회하면 알고리즘마다 같은 4개 쿼리가 반복된다.
     * @param loanSchedules   호출자가 상위에서 한 번만 조회해 넘기는 기존 대출 스케줄.
     *                        DSR 여유분 계산의 기존 상환액 합계를 여기서 구한다.
     * @param effectiveSaving 호출자가 넘기는 월 순저축액(기존 대출 상환액 등을 이미 차감한 값).
     *                        loanO 달성 가능 여부(capacity) 판정에 쓴다. 서버 캐시가 아니라 이 값을 기준으로
     *                        삼아, 추천 요청이 넘긴 저축액과 예산 계산이 어긋나지 않게 한다.
     */
    public LoanPlans calculate(long memberId, long requiredAmount, YearMonth targetDate,
                               AssetNetWorthBreakdown netWorth, List<LoanSchedule> loanSchedules,
                               long effectiveSaving) {
        long months = ChronoUnit.MONTHS.between(YearMonth.now(), targetDate);

        long loanXSaving = calcMonthlySavingNeeded(netWorth, requiredAmount, months);
        GoalRecommendationResponse.LoanXPlan loanX = GoalRecommendationResponse.LoanXPlan.builder()
                .targetAmount(requiredAmount)
                .targetDate(targetDate)
                .monthlySaving(loanXSaving)
                .build();

        long existingPayment = totalMonthlyPayment(loanSchedules);
        long loanAmount = Math.min(calcMaxLoanAmount(memberId, existingPayment), requiredAmount);
        if (loanAmount <= 0) {
            return LoanPlans.builder().loanX(loanX).loanO(null).build();
        }

        long selfFunded = requiredAmount - loanAmount;
        long loanOSaving = calcMonthlySavingNeeded(netWorth, selfFunded, months);

        // 실제 저축 가능액(호출자가 넘긴 순저축액) 초과 시 달성 불가 → loanO null
        if (loanOSaving > effectiveSaving) {
            return LoanPlans.builder().loanX(loanX).loanO(null).build();
        }

        GoalRecommendationResponse.LoanOPlan loanO = GoalRecommendationResponse.LoanOPlan.builder()
                .loanAmount(loanAmount)
                .targetAmount(selfFunded)
                .targetDate(targetDate)
                .monthlySaving(loanOSaving)
                .build();

        return LoanPlans.builder().loanX(loanX).loanO(loanO).build();
    }

    /**
     * 월 저축액을 고정하고 도달 시점을 계산하는 플랜.
     *
     * <p>{@link #calculate}가 "목표 시점 고정 → 필요 저축액 역산"이라면, 이 메서드는 정확히 반대다.
     * 사용자의 실제 월 저축액을 그대로 두고 목표 금액에 <b>언제</b> 도달하는지를 계산한다.
     * loanX·loanO의 {@code monthlySaving}에는 넘겨받은 rawMonthlySaving이 그대로 담기고,
     * {@code targetDate}가 계산 결과다. 저축액으로 상한(1200개월) 내 도달이 불가능하면 targetDate는 null이다.
     *
     * <p>도달 시점 계산에는 {@code loanSchedules}를 사용해 대출별 잔여 기간을 구간별로 반영한다.
     * 대출이 끝난 달부터는 rawMonthlySaving 전액이 저축된다.
     *
     * @param requiredAmount    도달해야 할 목표 금액(원)
     * @param netWorth          현재 순자산 구성
     * @param rawMonthlySaving  사용자 입력 월 저축액(원) — 카드 표시값
     * @param loanSchedules     기존 대출 스케줄 목록 — 구간별 계산용
     */
    public LoanPlans calculateSavingFixed(long memberId, long requiredAmount,
                                          AssetNetWorthBreakdown netWorth,
                                          long rawMonthlySaving,
                                          List<LoanSchedule> loanSchedules) {
        YearMonth now = YearMonth.now();

        Long loanXMonths = budgetCalculator.monthsToReach(netWorth, rawMonthlySaving, loanSchedules, requiredAmount);
        GoalRecommendationResponse.LoanXPlan loanX = GoalRecommendationResponse.LoanXPlan.builder()
                .targetAmount(requiredAmount)
                .targetDate(loanXMonths != null ? now.plusMonths(loanXMonths) : null)
                .monthlySaving(rawMonthlySaving)
                .build();

        long existingPayment = totalMonthlyPayment(loanSchedules);
        long loanAmount = Math.min(calcMaxLoanAmount(memberId, existingPayment), requiredAmount);
        if (loanAmount <= 0) {
            return LoanPlans.builder().loanX(loanX).loanO(null).build();
        }

        // 대출금은 즉시 가용 자금으로 편입한다.
        // 신규 대출 상환은 DSR 40% 한도 내에서 별도 소득으로 충당되므로 저축액에서 차감하지 않는다.
        // 기존 대출 스케줄은 그대로 넘겨 구간별 계산이 계속 반영되도록 한다.
        AssetNetWorthBreakdown netWorthWithLoan = AssetNetWorthBreakdown.builder()
                .interestBearingAssets(netWorth.getInterestBearingAssets())
                .flatRecognizedAssets(netWorth.getFlatRecognizedAssets() + loanAmount)
                .build();

        Long loanOMonths = budgetCalculator.monthsToReach(netWorthWithLoan, rawMonthlySaving, loanSchedules, requiredAmount);
        if (loanOMonths == null) {
            return LoanPlans.builder().loanX(loanX).loanO(null).build();
        }

        Long shortened = loanXMonths != null ? Math.max(0, loanXMonths - loanOMonths) : null;
        GoalRecommendationResponse.LoanOPlan loanO = GoalRecommendationResponse.LoanOPlan.builder()
                .loanAmount(loanAmount)
                .targetAmount(requiredAmount - loanAmount)
                .targetDate(now.plusMonths(loanOMonths))
                .monthlySaving(rawMonthlySaving)
                .shortenedMonths(shortened)
                .build();

        return LoanPlans.builder().loanX(loanX).loanO(loanO).build();
    }

    /**
     * 대출 계좌별 잔여 상환 스케줄 목록을 반환한다.
     *
     * <p>end_date가 없거나 이미 만기된 대출은 제외한다.
     * 각 건의 월 원리금은 잔액과 잔여 기간으로 {@code ASSUMED_LOAN_ANNUAL_RATE} 기준 역산한다.
     */
    public List<LoanSchedule> getLoanSchedules(long memberId) {
        double r = ASSUMED_LOAN_ANNUAL_RATE / 12.0;
        return loanAccountService.getLoanAccounts(memberId).stream()
                .filter(loan -> loan.getLoanBalance() != null && loan.getLoanBalance() > 0
                        && loan.getEndDate() != null)
                .map(loan -> {
                    long remaining = ChronoUnit.MONTHS.between(LocalDate.now(), loan.getEndDate());
                    if (remaining <= 0) return null;
                    double f = Math.pow(1 + r, remaining);
                    long payment = Math.round(loan.getLoanBalance() * r * f / (f - 1));
                    return new LoanSchedule(payment, remaining);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * 이미 확보해 둔 대출 스케줄에서 월 원리금 합계를 구한다.
     *
     * <p>{@link #getLoanSchedules(long)}가 상환 부담 없는 대출(잔액 0·만기 도래·end_date 없음)을
     * 이미 걸러 내므로, 스케줄 합계는 {@link #calcTotalExistingMonthlyPayment(long)}과 같은 값이다.
     * {@code MemberFinancialContext.currentEffectiveSaving()}도 같은 방식으로 합산한다.
     * 상위에서 한 번 조회한 스케줄을 재사용해 계좌 조회 쿼리 반복을 없앤다.
     */
    private long totalMonthlyPayment(List<LoanSchedule> loanSchedules) {
        return loanSchedules.stream().mapToLong(LoanSchedule::monthlyPayment).sum();
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
        return calcMaxLoanAmount(memberId, calcTotalExistingMonthlyPayment(memberId));
    }

    private long calcMaxLoanAmount(long memberId, long existingMonthlyPayment) {
        Long monthlyIncome = memberService.getMonthlyIncome(memberId);
        if (monthlyIncome == null || monthlyIncome <= 0) return 0L;

        double r = ASSUMED_LOAN_ANNUAL_RATE / 12.0;
        double factor = Math.pow(1 + r, NEW_LOAN_TERM_MONTHS);

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
