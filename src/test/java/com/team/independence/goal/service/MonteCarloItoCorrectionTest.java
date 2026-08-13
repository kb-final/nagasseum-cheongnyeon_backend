package com.team.independence.goal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MonteCarloEngine Itô 보정 회귀 테스트
 *
 * PriceModel.annualDrift는 산술 드리프트 μ(= logDrift + σ²/2)를 저장한다.
 * MonteCarloEngine은 이 값을 받아 내부에서 Itô 보정(−σ²/2)을 적용하므로
 * exponent = logDrift × T가 되어 GBM 중앙값과 정확히 일치해야 한다.
 *
 * 이중 보정 버그(annualDrift = logDrift였을 때 priceP50이 ~5.8% 낮았던 문제)가
 * 재발하지 않도록 두 케이스를 모두 고정한다.
 */
class MonteCarloItoCorrectionTest {

    private static final double MU = 0.08;
    private static final double SIGMA = 0.20;
    private static final double LOG_DRIFT = MU - 0.5 * SIGMA * SIGMA;  // = 0.06

    private static final long INITIAL_PRICE = 100_000_000L;
    private static final int FORECAST_MONTHS = 36;
    private static final double T = FORECAST_MONTHS / 12.0;

    private static final long THEORETICAL_MEDIAN =
            Math.round(INITIAL_PRICE * Math.exp(LOG_DRIFT * T));

    @Test
    @DisplayName("산술 드리프트 μ를 전달하면 priceP50이 GBM 중앙값과 2% 이내로 일치한다")
    void arithmeticDrift_matchesTheoreticalMedian() {
        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                MU, SIGMA, INITIAL_PRICE, Long.MAX_VALUE,
                FORECAST_MONTHS, 100_000, 42L);

        assertEquals(THEORETICAL_MEDIAN, result.priceP50(), THEORETICAL_MEDIAN * 0.02,
                String.format("p50(%,d)이 GBM 중앙값(%,d)과 2%% 이상 차이남",
                        result.priceP50(), THEORETICAL_MEDIAN));
    }

    @Test
    @DisplayName("로그드리프트를 μ 자리에 넣으면 priceP50이 이론치보다 낮게 편향된다 — 이 동작이 사라지면 버그 재발")
    void logDrift_as_mu_underestimates_by_half_sigma_squared() {
        // Itô 이중 보정 시 exponent = (logDrift − σ²/2) × T
        long biasedMedian = Math.round(INITIAL_PRICE * Math.exp((LOG_DRIFT - 0.5 * SIGMA * SIGMA) * T));

        MonteCarloEngine.Result result = MonteCarloEngine.simulate(
                LOG_DRIFT, SIGMA, INITIAL_PRICE, Long.MAX_VALUE,
                FORECAST_MONTHS, 100_000, 42L);

        assertTrue(result.priceP50() < THEORETICAL_MEDIAN,
                "logDrift를 μ 자리에 넣으면 p50이 이론치보다 낮아야 한다");
        assertEquals(biasedMedian, result.priceP50(), biasedMedian * 0.03,
                String.format("p50(%,d)이 이중 보정 기댓값(%,d)과 다르다", result.priceP50(), biasedMedian));
    }
}
