package com.team.independence.property.service;

import java.util.List;

/**
 * 월별 평단가 시계열에서 연간 성장률(μ), 연율 변동성(σ)을 추정한다.
 *
 * 두 시점 CAGR 대신 ln(price) ~ t OLS 회귀로 기울기를 뽑는다.
 * 엔드포인트 노이즈에 강하고, μ와 σ를 같은 시계열에서 한 번의 순회로 뽑으므로
 * 몬테카를로가 이 결과를 그대로 입력으로 쓸 수 있다.
 *
 * @param annualDrift 연간 산술 드리프트(μ): 몬테카를로 drift 입력값.
 *                    OLS 회귀 기울기는 로그드리프트(μ − σ²/2)이므로, σ²/2를 더해 μ로 복원한다.
 *                    MonteCarloEngine이 내부에서 Itô 보정(−σ²/2)을 직접 적용하므로
 *                    이 필드는 반드시 산술 드리프트 μ여야 한다.
 * @param cagr 표시용 연 상승률(중앙 경로 기준): Math.exp(logDrift) - 1
 * @param annualVol 연율 변동성(σ): 몬테카를로 volatility 입력값
 * @param months 회귀에 사용된 유효 표본 월 수
 */
public record PriceModel(double annualDrift, double cagr, double annualVol, int months) {

    /**
     * @param pricePerPyeong 시간순 정렬된 월별 평단가 목록. 최소 6개 이상이어야 한다.
     */
    public static PriceModel estimate(List<Double> pricePerPyeong) {
        int n = pricePerPyeong.size();

        // ln(price) ~ t 최소제곱 회귀 → 월간 로그드리프트(기울기) = (μ − σ²/2) / 12
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int t = 0; t < n; t++) {
            double y = Math.log(pricePerPyeong.get(t));
            sx += t;
            sy += y;
            sxx += (double) t * t;
            sxy += t * y;
        }
        double slope = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        double logDrift = slope * 12;  // 연간 로그드리프트 = (μ − σ²/2)
        double cagr = Math.exp(logDrift) - 1;  // 중앙 경로 기준 연 상승률

        // 월별 로그수익률 표준편차 → 연율 변동성 σ
        int k = n - 1;
        double[] r = new double[k];
        double mean = 0;
        for (int t = 1; t < n; t++) {
            r[t - 1] = Math.log(pricePerPyeong.get(t) / pricePerPyeong.get(t - 1));
            mean += r[t - 1];
        }
        mean /= k;
        double var = 0;
        for (double x : r) var += (x - mean) * (x - mean);
        var /= (k - 1);
        double annualVol = Math.sqrt(var) * Math.sqrt(12);

        // 산술 드리프트 μ = logDrift + σ²/2
        // MonteCarloEngine이 (annualDrift − σ²/2) × T를 exponent로 쓰므로
        // annualDrift는 반드시 μ(산술)여야 Itô 보정이 정확히 한 번만 적용된다.
        double annualDrift = logDrift + 0.5 * annualVol * annualVol;

        return new PriceModel(annualDrift, cagr, annualVol, n);
    }
}
