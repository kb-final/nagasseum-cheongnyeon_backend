package com.team.independence.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MonteCarloEngine.simulate() 단위 테스트
 *
 * 순수 계산이라 Spring 컨텍스트 없이 실행된다.
 * 시드를 고정하여 재현성을 보장한다.
 */
class MonteCarloEngineTest {

    private static final long INITIAL_PRICE = 300_000_000L;
    private static final int SIMULATIONS = 10_000;
    private static final long SEED = 42L;

    // ------------------------------------------------------------------
    // σ = 0 결정적 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("σ=0 → 모든 경로가 같은 값 (P5 == P50 == P95)")
    void 변동성_0_결정적_수렴() {
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                0.05, 0.0, INITIAL_PRICE, Long.MAX_VALUE, 24, SIMULATIONS, SEED);

        assertEquals(result.priceP5(), result.priceP50(), "σ=0 → P5 == P50");
        assertEquals(result.priceP50(), result.priceP95(), "σ=0 → P50 == P95");
        assertEquals(1.0, result.successProbability(), 0.001);
    }

    @Test
    @DisplayName("μ=0, σ=0 → P50 ≈ P(0) (드리프트, 변동성 없음)")
    void 드리프트_0_변동성_0_가격_불변() {
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                0.0, 0.0, INITIAL_PRICE, 0L, 12, SIMULATIONS, SEED);

        assertEquals(INITIAL_PRICE, result.priceP50(), INITIAL_PRICE * 0.01,
                "μ=σ=0 → P50 ≈ P(0)");
    }

    // ------------------------------------------------------------------
    // 드리프트 방향성
    // ------------------------------------------------------------------

    @Test
    @DisplayName("μ>0, σ=0 → P50 > P(0) (양의 드리프트로 가격 상승)")
    void 양의_드리프트_가격_상승() {
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                0.10, 0.0, INITIAL_PRICE, 0L, 12, SIMULATIONS, SEED);

        assertTrue(result.priceP50() > INITIAL_PRICE,
                "μ>0 → P50 > P(0). 실제 P50=" + result.priceP50());
    }

    // ------------------------------------------------------------------
    // 분포 형태
    // ------------------------------------------------------------------

    @Test
    @DisplayName("σ>0 → P5 < P50 < P95 (변동성이 분포를 펼침)")
    void 변동성_있으면_분포_펼쳐짐() {
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                0.05, 0.20, INITIAL_PRICE, 0L, 36, SIMULATIONS, SEED);

        assertTrue(result.priceP5() < result.priceP50(), "P5 < P50");
        assertTrue(result.priceP50() < result.priceP95(), "P50 < P95");
    }

    // ------------------------------------------------------------------
    // 성공 확률
    // ------------------------------------------------------------------

    @Test
    @DisplayName("예산이 P(0)×100 → 성공 확률 ≈ 1.0")
    void 예산_충분하면_성공률_1() {
        long hugeBudget = INITIAL_PRICE * 100L;
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                0.05, 0.15, INITIAL_PRICE, hugeBudget, 12, SIMULATIONS, SEED);

        assertEquals(1.0, result.successProbability(), 0.001,
                "압도적 예산 → 성공률 ≈ 1");
    }

    @Test
    @DisplayName("예산=0 → 성공 확률 ≈ 0.0")
    void 예산_0이면_성공률_0() {
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                0.05, 0.15, INITIAL_PRICE, 0L, 12, SIMULATIONS, SEED);

        assertEquals(0.0, result.successProbability(), 0.001,
                "예산=0 → 성공률 ≈ 0");
    }

    // ------------------------------------------------------------------
    // 예외
    // ------------------------------------------------------------------

    @Test
    @DisplayName("months <= 0이면 IllegalArgumentException")
    void 기간이_0이하면_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloEngine.simulate(0.05, 0.10, INITIAL_PRICE, 0L, 0, SIMULATIONS, SEED));
    }
}
