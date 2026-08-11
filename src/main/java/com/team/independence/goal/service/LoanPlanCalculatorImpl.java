package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.LoanPlans;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ⚠️ 임시 스텁 — 실제 계산 로직이 없습니다.
 *
 * <p><b>이 클래스는 구현하지 마세요.</b> 담당자가 따로 채울 예정입니다.
 * 추천 알고리즘 담당자들이 {@link LoanPlanCalculator} 구현을 기다리지 않고 바로 개발을 시작할 수
 * 있도록, 호출하면 고정된 더미 값을 돌려주는 껍데기만 먼저 올려 둔 것입니다.
 *
 * <p>알고리즘 쪽에서는 평소처럼 {@link LoanPlanCalculator}를 주입받아 호출하면 됩니다.
 * 지금은 값이 가짜지만 시그니처와 반환 구조는 최종본과 동일하므로,
 * 나중에 이 클래스 내용만 채워지면 알고리즘 코드는 손대지 않아도 됩니다.
 *
 * <p>더미 값이라는 것을 한눈에 알 수 있도록 월 저축액 100만원, 대출 한도 1억으로 고정해 두었습니다.
 * 화면에 이 숫자가 그대로 보인다면 아직 계산기가 안 붙은 것입니다.
 */
@Service
@RequiredArgsConstructor
public class LoanPlanCalculatorImpl implements LoanPlanCalculator {

    /** 더미 월 저축액 (원) */
    private static final long STUB_MONTHLY_SAVING = 1_000_000L;

    /** 더미 대출 한도 (원) */
    private static final long STUB_LOAN_AMOUNT = 100_000_000L;

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ 스텁: 입력을 그대로 되돌려주고 나머지는 더미 값으로 채웁니다.
     * 소득·자산 조회, DSR 한도 산출, 복리 저축 역산 모두 아직 없습니다.
     */
    @Override
    public LoanPlans calculate(long memberId, long requiredAmount, YearMonth targetDate) {
        GoalRecommendationResponse.LoanXPlan loanX = GoalRecommendationResponse.LoanXPlan.builder()
                .targetAmount(requiredAmount)
                .targetDate(targetDate)
                .monthlySaving(STUB_MONTHLY_SAVING)
                .build();

        GoalRecommendationResponse.LoanOPlan loanO = GoalRecommendationResponse.LoanOPlan.builder()
                .loanAmount(STUB_LOAN_AMOUNT)
                .targetAmount(Math.max(requiredAmount - STUB_LOAN_AMOUNT, 0L))
                .targetDate(targetDate)
                .monthlySaving(STUB_MONTHLY_SAVING)
                .build();

        return LoanPlans.builder()
                .loanX(loanX)
                .loanO(loanO)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ 스텁: 회원과 무관하게 고정 한도를 반환합니다.
     */
    @Override
    public long calcMaxLoanAmount(long memberId) {
        return STUB_LOAN_AMOUNT;
    }
}
