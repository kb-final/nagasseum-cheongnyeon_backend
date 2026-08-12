package com.team.independence.property.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PriceModel.estimate() 단위 테스트.
 *
 * <p>순수 계산이라 Spring 컨텍스트 없이 실행된다.
 * 기대값은 입력 시계열을 직접 생성할 때 심은 파라미터에서 역산한다.
 */
class  PriceModelTest {

    private static final double TOLERANCE = 0.01; // 허용 오차 1%p

    // ------------------------------------------------------------------
    // CAGR 추정
    // ------------------------------------------------------------------

    @Test
    @DisplayName("노이즈 없이 연 5% 성장하는 시계열 → CAGR 약 5%")
    void 연5퍼_성장_시계열_CAGR() {
        double annualRate = 0.05;
        List<Double> series = growingSeries(1_000_000, annualRate, 36);

        PriceModel model = PriceModel.estimate(series);

        assertEquals(annualRate, model.cagr(), TOLERANCE);
    }

    @Test
    @DisplayName("노이즈 없이 연 10% 성장하는 시계열 → CAGR 약 10%")
    void 연10퍼_성장_시계열_CAGR() {
        double annualRate = 0.10;
        List<Double> series = growingSeries(2_000_000, annualRate, 36);

        PriceModel model = PriceModel.estimate(series);

        assertEquals(annualRate, model.cagr(), TOLERANCE);
    }

    @Test
    @DisplayName("가격이 고정된 시계열 → CAGR 약 0%")
    void 가격_고정_시계열_CAGR_0() {
        List<Double> series = flatSeries(1_500_000, 36);

        PriceModel model = PriceModel.estimate(series);

        assertEquals(0.0, model.cagr(), TOLERANCE);
    }

    @Test
    @DisplayName("연 5% 성장 시계열의 annualDrift → ln(1.05) ≈ 0.0488")
    void annualDrift_연속복리_검증() {
        double annualRate = 0.05;
        List<Double> series = growingSeries(1_000_000, annualRate, 36);

        PriceModel model = PriceModel.estimate(series);

        double expectedDrift = Math.log(1 + annualRate);
        assertEquals(expectedDrift, model.annualDrift(), TOLERANCE);
    }

    // ------------------------------------------------------------------
    // 변동성(σ) 추정
    // ------------------------------------------------------------------

    @Test
    @DisplayName("노이즈 없는 시계열 → σ ≈ 0")
    void 노이즈_없는_시계열_변동성_0() {
        List<Double> series = growingSeries(1_000_000, 0.05, 36);

        PriceModel model = PriceModel.estimate(series);

        assertEquals(0.0, model.annualVol(), TOLERANCE);
    }

    @Test
    @DisplayName("노이즈를 심은 시계열 → σ > 0")
    void 노이즈_있는_시계열_변동성_양수() {
        List<Double> series = noisySeries(1_000_000, 0.05, 0.10, 36);

        PriceModel model = PriceModel.estimate(series);

        assertTrue(model.annualVol() > 0,
                "노이즈가 있으면 σ가 0보다 커야 한다. 실제=" + model.annualVol());
    }

    // ------------------------------------------------------------------
    // months 필드
    // ------------------------------------------------------------------

    @Test
    @DisplayName("36개월 시계열 → months = 36")
    void months_필드_검증() {
        List<Double> series = growingSeries(1_000_000, 0.05, 36);

        PriceModel model = PriceModel.estimate(series);

        assertEquals(36, model.months());
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    /** 초기값에서 연간 annualRate로 복리 성장하는 n개월 시계열 (노이즈 없음). */
    private List<Double> growingSeries(double initial, double annualRate, int n) {
        double monthlyRate = Math.pow(1 + annualRate, 1.0 / 12) - 1;
        List<Double> series = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            series.add(initial * Math.pow(1 + monthlyRate, t));
        }
        return series;
    }

    /** 모든 월이 동일한 가격인 n개월 시계열. */
    private List<Double> flatSeries(double price, int n) {
        List<Double> series = new ArrayList<>(n);
        for (int i = 0; i < n; i++) series.add(price);
        return series;
    }

    /**
     * 연간 annualRate로 성장하되 월별로 vol 크기의 노이즈를 더한 n개월 시계열.
     * 노이즈는 결정적(시드 고정 대신 sin 함수)으로 생성해 재현성을 확보한다.
     */
    private List<Double> noisySeries(double initial, double annualRate, double vol, int n) {
        double monthlyRate = Math.pow(1 + annualRate, 1.0 / 12) - 1;
        List<Double> series = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            double trend = initial * Math.pow(1 + monthlyRate, t);
            double noise = trend * vol * Math.sin(t);
            series.add(trend + noise);
        }
        return series;
    }
}
