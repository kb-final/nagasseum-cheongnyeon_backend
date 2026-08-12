package com.team.independence.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.team.independence.asset.dto.account.LoanAccountDetailItem;
import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.asset.service.LoanAccountService;
import com.team.independence.goal.dto.LoanPlans;
import com.team.independence.goal.service.calculator.BudgetCalculator;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.member.dto.MemberProfileResponse;
import com.team.independence.member.service.MemberService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanPlanCalculatorTest {

    @Mock MemberService      memberService;
    @Mock LoanAccountService loanAccountService;
    @Mock AssetSummaryService assetSummaryService;

    private LoanPlanCalculator calculator;

    /** 기준 월 소득 (원) */
    private static final long MONTHLY_INCOME = 4_000_000L;

    /** 테스트 공통 memberId */
    private static final long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        calculator = new LoanPlanCalculator(
                memberService,
                loanAccountService,
                assetSummaryService,
                new BudgetCalculator()
        );
    }

    // -----------------------------------------------------------------------
    // calcMaxLoanAmount
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("소득이 null이면 대출 한도는 0")
    void 소득_null이면_대출한도_0() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(null));

        assertEquals(0L, calculator.calcMaxLoanAmount(MEMBER_ID));
    }

    @Test
    @DisplayName("소득이 0이면 대출 한도는 0")
    void 소득_0이면_대출한도_0() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(0L));

        assertEquals(0L, calculator.calcMaxLoanAmount(MEMBER_ID));
    }

    @Test
    @DisplayName("기존 대출이 없으면 월소득 × 40% 전부를 신규 대출 상환에 쓸 수 있다")
    void 기존대출_없으면_DSR_전체_여유() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(MONTHLY_INCOME));
        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of());

        long maxLoan = calculator.calcMaxLoanAmount(MEMBER_ID);

        // 월 소득 400만 × 40% = 160만 → 30년 연 3.5% 기준 대출 가능액
        // 정확한 값 검증 대신 합리적 범위 확인 (약 3.5억 수준)
        assertTrue(maxLoan > 300_000_000L, "대출 한도가 3억 이상이어야 한다");
        assertTrue(maxLoan < 500_000_000L, "대출 한도가 5억 미만이어야 한다");
    }

    @Test
    @DisplayName("기존 대출이 DSR 한도를 꽉 채우면 신규 대출 한도는 0")
    void 기존대출_DSR_초과시_신규한도_0() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(MONTHLY_INCOME));

        // 기존 대출: 잔액 5억, 잔여 30년 → 월 상환액이 DSR 40%(160만)를 이미 초과
        LoanAccountDetailItem heavyLoan = loanItem(500_000_000L, LocalDate.now().plusYears(30));
        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of(heavyLoan));

        assertEquals(0L, calculator.calcMaxLoanAmount(MEMBER_ID));
    }

    @Test
    @DisplayName("기존 대출이 있으면 한도가 줄어든다")
    void 기존대출_있으면_한도_감소() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(MONTHLY_INCOME));

        long maxWithout = maxLoanWithNoExistingDebt();

        LoanAccountDetailItem existingLoan = loanItem(50_000_000L, LocalDate.now().plusYears(10));
        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of(existingLoan));

        long maxWith = calculator.calcMaxLoanAmount(MEMBER_ID);

        assertTrue(maxWith < maxWithout, "기존 대출이 있으면 한도가 줄어야 한다");
    }

    @Test
    @DisplayName("만기가 지난 대출은 DSR 부담에서 제외된다")
    void 만기_지난_대출은_DSR_무시() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(MONTHLY_INCOME));

        // 이미 만기된 대출
        LoanAccountDetailItem expiredLoan = loanItem(100_000_000L, LocalDate.now().minusDays(1));
        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of(expiredLoan));

        long maxWithExpired = calculator.calcMaxLoanAmount(MEMBER_ID);

        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of());
        long maxWithNone = calculator.calcMaxLoanAmount(MEMBER_ID);

        assertEquals(maxWithNone, maxWithExpired, "만기된 대출은 한도 계산에 영향 없어야 한다");
    }

    // -----------------------------------------------------------------------
    // calculate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("소득이 없으면 loanO가 null이다")
    void 소득없으면_loanO_null() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(null));
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(netWorth(0L, 0L));

        LoanPlans plans = calculator.calculate(MEMBER_ID, 200_000_000L, YearMonth.now().plusMonths(36));

        assertNotNull(plans.getLoanX());
        assertNull(plans.getLoanO(), "대출 한도 0이면 loanO는 null이어야 한다");
    }

    @Test
    @DisplayName("대출이 목표금액 이상이면 loanO의 월 저축액은 0")
    void 대출이_목표초과시_loanO_저축_0() {
        // 대출 한도 > 목표 금액 → selfFunded = 0
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(10_000_000L)); // 고소득
        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of());
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(netWorth(0L, 0L));

        long requiredAmount = 50_000_000L; // 5천만 (대출 한도보다 훨씬 작음)
        LoanPlans plans = calculator.calculate(MEMBER_ID, requiredAmount, YearMonth.now().plusMonths(24));

        assertNotNull(plans.getLoanO());
        assertEquals(0L, plans.getLoanO().getMonthlySaving(), "대출으로 전액 충당되면 월 저축 0이어야 한다");
        assertEquals(requiredAmount, plans.getLoanO().getLoanAmount(), "대출액은 목표금액으로 캡이어야 한다");
        assertEquals(0L, plans.getLoanO().getTargetAmount(), "자기자금 부담은 0이어야 한다");
    }

    @Test
    @DisplayName("loanO의 월 저축액은 loanX보다 작다")
    void loanO_월저축이_loanX보다_작다() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(MONTHLY_INCOME));
        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of());
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(netWorth(10_000_000L, 5_000_000L));

        // 현재 자산으로는 부족하고 저축이 필요한 규모
        LoanPlans plans = calculator.calculate(MEMBER_ID, 200_000_000L, YearMonth.now().plusMonths(48));

        assertNotNull(plans.getLoanO());
        assertTrue(plans.getLoanO().getMonthlySaving() < plans.getLoanX().getMonthlySaving(),
                "대출을 끼면 월 저축 부담이 줄어야 한다");
    }

    @Test
    @DisplayName("loanX의 목표금액과 targetDate는 입력값 그대로다")
    void loanX_필드_검증() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(null));
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(netWorth(0L, 0L));

        long requiredAmount = 150_000_000L;
        YearMonth targetDate = YearMonth.now().plusMonths(30);
        LoanPlans plans = calculator.calculate(MEMBER_ID, requiredAmount, targetDate);

        assertEquals(requiredAmount, plans.getLoanX().getTargetAmount());
        assertEquals(targetDate, plans.getLoanX().getTargetDate());
    }

    @Test
    @DisplayName("이미 자산이 목표금액 이상이면 월 저축액은 0")
    void 자산이_목표초과시_저축_0() {
        when(memberService.getMember(MEMBER_ID)).thenReturn(profile(null));
        // 현재 자산 2억 > 목표 1억
        when(assetSummaryService.getNetWorthBreakdown(MEMBER_ID)).thenReturn(netWorth(200_000_000L, 0L));

        LoanPlans plans = calculator.calculate(MEMBER_ID, 100_000_000L, YearMonth.now().plusMonths(24));

        assertEquals(0L, plans.getLoanX().getMonthlySaving(), "자산이 이미 충분하면 저축 0이어야 한다");
    }

    // -----------------------------------------------------------------------
    // 헬퍼
    // -----------------------------------------------------------------------

    private MemberProfileResponse profile(Long monthlyIncome) {
        return new MemberProfileResponse(MEMBER_ID, "테스터", null, monthlyIncome, null, false, false);
    }

    private AssetNetWorthBreakdown netWorth(long interestBearing, long flat) {
        return AssetNetWorthBreakdown.builder()
                .interestBearingAssets(interestBearing)
                .flatRecognizedAssets(flat)
                .build();
    }

    private LoanAccountDetailItem loanItem(long balance, LocalDate endDate) {
        LoanAccountDetailItem item = new LoanAccountDetailItem();
        item.setLoanBalance(balance);
        item.setEndDate(endDate);
        return item;
    }

    private long maxLoanWithNoExistingDebt() {
        when(loanAccountService.getLoanAccounts(MEMBER_ID)).thenReturn(List.of());
        return calculator.calcMaxLoanAmount(MEMBER_ID);
    }
}
