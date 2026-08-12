package com.team.independence.property.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team.independence.config.RootConfig;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * PriceModel 실데이터 검증.
 *
 * <p>실행 순서:
 * <ol>
 *   <li>docker compose up -d</li>
 *   <li>@Disabled 제거</li>
 *   <li>backfill_강남구_36개월 먼저 실행 (MOLIT API 36회 호출 — 1~2분 소요)</li>
 *   <li>priceModel_실데이터_검증 실행 후 콘솔에서 μ·CAGR·σ 값 확인</li>
 * </ol>
 *
 * <p>사전 조건: docker compose up -d, 환경변수 MOLIT_SERVICE_KEY
 */
// @Disabled("로컬 MySQL + 국토부 API 환경 전용")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PriceModelIntegrationTest {

    private static final String REGION_CODE = "11680"; // 강남구
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private RentTransactionSyncService rentTransactionSyncService;

    @Autowired
    private PriceModelService priceModelService;

    /**
     * 강남구 아파트 36개월치 데이터를 MOLIT API에서 받아 적재한다.
     * 이미 적재된 달은 덮어쓴다(재적재 구조).
     */
    @Test
    @Order(1)
    void backfill_강남구_36개월() {
        YearMonth base = YearMonth.now();
        for (int i = 0; i < 36; i++) {
            String dealYm = base.minusMonths(i).format(YM);
            rentTransactionSyncService.collectAndSync(REGION_CODE, dealYm, HousingType.APT);
            System.out.println("적재 완료: " + dealYm);
        }
    }

    /**
     * PriceModel 결과를 출력하고 상식 범위(CAGR -10% ~ +30%)를 검증한다.
     *
     * <p>강남구 아파트 전세 기준 시장 감각: CAGR 3~8% 수준이면 정상.
     * 콘솔 출력을 보고 직접 판단한다.
     */
    @Test
    @Order(2)
    void priceModel_실데이터_검증() {
        PriceModelRequest request = new PriceModelRequest();
        request.setRegionCode(REGION_CODE);
        request.setHousingType(HousingType.APT);
        request.setDealType(DealType.JEONSE);
        request.setAreaMin(15);
        request.setAreaMax(25);

        PriceModelResponse response = priceModelService.estimate(request);

        System.out.println("=== PriceModel 실데이터 검증 결과 ===");
        System.out.printf("지역: %s %s%n", response.getRegionCode(), response.getRegionName());
        System.out.printf("조건: %s / %s%n", response.getHousingType(), response.getDealType());
        System.out.printf("기간: %s ~ %s (%d개월)%n",
                response.getStartYm(), response.getEndYm(), response.getMonths());
        System.out.printf("CAGR: %.2f%%%n", response.getCagr() * 100);
        System.out.printf("annualDrift: %.4f (μ)%n", response.getAnnualDrift());
        System.out.printf("annualVol: %.4f (σ)%n", response.getAnnualVol());

        assertTrue(response.getCagr() > -0.10 && response.getCagr() < 0.30,
                "CAGR이 상식 범위(-10% ~ +30%)를 벗어남: " + response.getCagr() * 100 + "%");
        assertTrue(response.getAnnualVol() >= 0,
                "σ가 음수일 수 없음: " + response.getAnnualVol());
        assertTrue(response.getMonths() >= 6,
                "유효 월 수가 최소 기준(6) 미만: " + response.getMonths());
    }
}
