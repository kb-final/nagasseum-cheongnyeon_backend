package com.team.independence.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 목표 금액 도달 개월수 계산 테스트.
 *
 * <p>calculateMonthToReach는 주입받은 의존성을 하나도 쓰지 않는 순수 계산이라
 * 생성자에 null을 넘겨 만든다. DB도 스프링 컨텍스트도 필요 없다.
 */
class GoalServiceImplTest {

    /** 연 5% 복리를 월 단위로 환산한 이자율. 서비스와 같은 식으로 기대값을 만든다. */
    private static final double MONTHLY_RATE = Math.pow(1.05, 1.0 / 12) - 1;

    private GoalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoalServiceImpl(null, null, null, null, null, null, null);
    }

    // ------------------------------------------------------------------
    // 핵심 성질: 진단이 만든 목표 금액을 그대로 되짚는다
    // ------------------------------------------------------------------

    @Test
    @DisplayName("저축액 S로 M개월 굴려 나온 금액을 목표로 주면 정확히 M개월이 나온다")
    void 진단이_만든_목표금액을_그대로_되짚는다() {
        long growingAssets = 30_000_000L;
        long fixedAssets = 20_000_000L;
        long monthlySaving = 1_500_000L;
        long months = 17;

        // 목표 저장 시 target_amount에 들어가는 값과 같은 방식으로 만든다.
        long targetAmount = grown(growingAssets, months) + fixedAssets
                + projectedSavings(monthlySaving, months);

        Long actual = service.calculateMonthToReach(
                netWorth(growingAssets, fixedAssets), monthlySaving, targetAmount);

        assertEquals(months, actual.longValue());
    }

    @Test
    @DisplayName("목표 금액이 1원만 더 커도 한 달이 더 걸린다")
    void 목표가_조금이라도_크면_한달이_더_걸린다() {
        long growingAssets = 30_000_000L;
        long fixedAssets = 20_000_000L;
        long monthlySaving = 1_500_000L;
        long months = 17;

        long exactAmount = grown(growingAssets, months) + fixedAssets
                + projectedSavings(monthlySaving, months);

        Long actual = service.calculateMonthToReach(
                netWorth(growingAssets, fixedAssets), monthlySaving, exactAmount + 1);

        assertEquals(months + 1, actual.longValue());
    }

    // ------------------------------------------------------------------
    // 경계값
    // ------------------------------------------------------------------

    @Test
    @DisplayName("이미 목표 이상을 모았으면 0개월")
    void 이미_달성했으면_0개월() {
        Long actual = service.calculateMonthToReach(
                netWorth(50_000_000L, 50_000_000L), 1_000_000L, 100_000_000L);

        assertEquals(0L, actual.longValue());
    }

    @Test
    @DisplayName("목표와 자산이 정확히 같아도 달성으로 본다")
    void 목표와_자산이_같으면_달성() {
        Long actual = service.calculateMonthToReach(
                netWorth(40_000_000L, 60_000_000L), 1_000_000L, 100_000_000L);

        assertEquals(0L, actual.longValue());
    }

    @Test
    @DisplayName("저축액이 0이면 계산할 수 없어 null")
    void 저축액_0이면_null() {
        assertNull(service.calculateMonthToReach(
                netWorth(10_000_000L, 0L), 0L, 300_000_000L));
    }

    @Test
    @DisplayName("저축액이 음수여도 null")
    void 저축액_음수면_null() {
        assertNull(service.calculateMonthToReach(
                netWorth(10_000_000L, 0L), -100_000L, 300_000_000L));
    }

    @Test
    @DisplayName("자산이 0이어도 저축액이 있으면 언젠가는 도달한다")
    void 자산이_없어도_저축으로_도달한다() {
        Long actual = service.calculateMonthToReach(
                netWorth(0L, 0L), 1_000_000L, 12_000_000L);

        assertNotNull(actual);
        // 이자가 붙으므로 단순 나눗셈(12개월)보다 빠르거나 같다.
        assertTrue(actual <= 12, "이자를 감안하면 12개월 이내여야 한다: " + actual);
    }

    @Test
    @DisplayName("탐색 상한을 넘길 만큼 저축액이 미미하면 null")
    void 사실상_도달_불가하면_null() {
        assertNull(service.calculateMonthToReach(
                netWorth(0L, 0L), 1L, 1_000_000_000_000L));
    }

    // ------------------------------------------------------------------
    // 단조성
    // ------------------------------------------------------------------

    @Test
    @DisplayName("저축액을 늘리면 도달 개월수가 줄어들거나 같다")
    void 저축액이_클수록_빨리_도달한다() {
        AssetNetWorthBreakdown netWorth = netWorth(10_000_000L, 5_000_000L);
        long targetAmount = 200_000_000L;

        long slow = service.calculateMonthToReach(netWorth, 1_000_000L, targetAmount);
        long normal = service.calculateMonthToReach(netWorth, 2_000_000L, targetAmount);
        long fast = service.calculateMonthToReach(netWorth, 3_000_000L, targetAmount);

        assertTrue(normal <= slow, "저축액을 올렸는데 더 오래 걸린다: " + slow + " -> " + normal);
        assertTrue(fast <= normal, "저축액을 올렸는데 더 오래 걸린다: " + normal + " -> " + fast);
    }

    @Test
    @DisplayName("이자 덕에 단순 나눗셈보다 빠르게 도달한다")
    void 이자가_붙어_단순계산보다_빠르다() {
        // 예적금 1억이 이자로 불어나므로, 남은 1억을 월 100만원으로 나눈 100개월보다 짧아야 한다.
        Long actual = service.calculateMonthToReach(
                netWorth(100_000_000L, 0L), 1_000_000L, 200_000_000L);

        assertNotNull(actual);
        assertTrue(actual < 100, "이자를 감안하면 100개월 미만이어야 한다: " + actual);
    }

    // ------------------------------------------------------------------
    // helper
    // ------------------------------------------------------------------

    private AssetNetWorthBreakdown netWorth(long growingAssets, long fixedAssets) {
        return AssetNetWorthBreakdown.builder()
                .interestBearingAssets(growingAssets)
                .flatRecognizedAssets(fixedAssets)
                .build();
    }

    /** 거치식 복리 (서비스의 calculateGrownAmount와 같은 식) */
    private long grown(long principal, long months) {
        return Math.round(principal * Math.pow(1 + MONTHLY_RATE, months));
    }

    /** 적립식 미래가치 (서비스의 calculateProjectedSavings와 같은 식) */
    private long projectedSavings(long monthlySaving, long months) {
        double factor = (Math.pow(1 + MONTHLY_RATE, months) - 1) / MONTHLY_RATE;
        return Math.round(monthlySaving * factor);
    }
}
