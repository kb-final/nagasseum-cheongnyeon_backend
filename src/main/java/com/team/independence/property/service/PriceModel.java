package com.team.independence.property.service;

import java.util.List;

/**
 * 월별 평단가 시계열에서 연간 성장률(μ), 연율 변동성(σ)을 추정한다.
 *
 * 두 시점 CAGR 대신 ln(price) ~ t OLS 회귀로 기울기를 뽑는다.
 * 엔드포인트 노이즈에 강하고, μ와 σ를 같은 시계열에서 한 번의 순회로 뽑으므로
 * 몬테카를로가 이 결과를 그대로 입력으로 쓸 수 있다.
 *
 * @param annualDrift 연간 연속 성장률(μ): 몬테카를로 drift 입력값
 * @param cagr 표시용 연 상승률: Math.exp(annualDrift) - 1
 * @param annualVol 연율 변동성(σ): 몬테카를로 volatility 입력값
 * @param months 회귀에 사용된 유효 표본 월 수
 */
public record PriceModel(double annualDrift, double cagr, double annualVol, int months) {

    /**
     * @param pricePerPyeong 시간순 정렬된 월별 평단가 목록. 최소 6개 이상이어야 한다.
     */
    public static PriceModel estimate(List<Double> pricePerPyeong) {
        int n = pricePerPyeong.size();

        // ln(price) ~ t 최소제곱 회귀 → 월간 연속성장률(기울기)
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int t = 0; t < n; t++) {
            double y = Math.log(pricePerPyeong.get(t));
            sx += t;
            sy += y;
            sxx += (double) t * t;
            sxy += t * y;
        }
        double slope = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        double annualDrift = slope * 12;
        double cagr = Math.exp(annualDrift) - 1;

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

        return new PriceModel(annualDrift, cagr, annualVol, n);
    }
}
