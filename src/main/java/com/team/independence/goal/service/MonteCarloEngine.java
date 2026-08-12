package com.team.independence.goal.service;

import java.util.Arrays;
import java.util.Random;

/**
 * GBM(기하 브라운 운동) 기반 몬테카를로 시뮬레이션 엔진
 * 순수 계산 클래스 — Spring 의존성 없음
 *
 * GBM: P(T) = P(0) × exp((μ − σ²/2) × T + σ × √T × Z), Z ~ N(0,1)
 * Itô 보정 (μ − σ²/2)은 필수. 빠뜨리면 기댓값이 P(0)×exp(μT)가 아닌 P(0)×exp((μ−σ²/2)T)가 된다.
 */
public class MonteCarloEngine {

    public static final int DEFAULT_SIMULATIONS = 10_000;
    public static final long DEFAULT_SEED = 42L;

    private MonteCarloEngine() {}

    public record Result(
        long priceP5,
        long priceP50,
        long priceP95,
        double successProbability
    ) {}

    /**
     * @param annualDrift  PriceModel.annualDrift (μ, 연속 복리 연율 드리프트)
     * @param annualVol    PriceModel.annualVol (σ, 연율 변동성)
     * @param initialPrice P(0) — 기준 시점의 주택 가격 (원)
     * @param budgetAtT    T 시점의 결정적 예상 예산 (원) — 성공 여부 판단 기준
     * @param months       시뮬레이션 기간 (개월, 양수)
     * @param simulations  경로 수
     * @param seed         난수 시드 (재현성 보장)
     */
    public static Result simulate(double annualDrift, double annualVol,
                                  long initialPrice, long budgetAtT,
                                  int months, int simulations, long seed) {
        if (months <= 0) {
            throw new IllegalArgumentException("months must be positive: " + months);
        }
        double T = months / 12.0;
        // Itô 보정된 드리프트: (μ - σ²/2) × T
        double drift = (annualDrift - 0.5 * annualVol * annualVol) * T;
        double diffusion = annualVol * Math.sqrt(T);

        Random rng = new Random(seed);
        long[] prices = new long[simulations];
        int successes = 0;

        for (int i = 0; i < simulations; i++) {
            double z = rng.nextGaussian();
            long price = Math.round(initialPrice * Math.exp(drift + diffusion * z));
            prices[i] = price;
            if (budgetAtT >= price) {
                successes++;
            }
        }

        Arrays.sort(prices);
        long p5  = prices[(int)(0.05 * simulations)];
        long p50 = prices[(int)(0.50 * simulations)];
        long p95 = prices[Math.min((int)(0.95 * simulations), simulations - 1)];
        double successProbability = (double) successes / simulations;

        return new Result(p5, p50, p95, successProbability);
    }
}
