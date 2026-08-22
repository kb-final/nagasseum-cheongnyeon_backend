package com.team.independence.property.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.config.RootConfig;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.MedianAggResult;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 중앙값 조회의 MyBatis 바인딩과 응답 조립을 실제 DB로 확인하는 통합 테스트.
 * RentTransactionSyncServiceIntegrationTest와 같은 유닛(11110/201512/APT)을 적재해두고 조회한다.
 *
 * <p>사전 조건: docker compose up -d, 환경변수 MOLIT_SERVICE_KEY
 */
@Disabled("실제 DB와 국토부 API가 필요해 로컬에서만 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class RentMedianIntegrationTest {

    private static final String REGION_CODE = "11110";
    private static final String DEAL_YM = "201512";

    /** 평수·보증금·월세를 사실상 무제한으로 두어 지역·유형·기간만으로 조회한다. */
    private static final long AREA_MIN_SQM = 0L;
    private static final long AREA_MAX_SQM = 999_999L;
    private static final long DEPOSIT_MIN = 0L;
    private static final long DEPOSIT_MAX = 999_999_999_999L;
    private static final long MONTHLY_RENT_MIN = 0L;
    private static final long MONTHLY_RENT_MAX = 999_999_999L;

    @Autowired
    private RentTransactionSyncService rentTransactionSyncService;

    @Autowired
    private RentTransactionMapper rentTransactionMapper;

    @Autowired
    private RentMedianService rentMedianService;

    @BeforeEach
    void 조회할_데이터_적재() {
        rentTransactionSyncService.collectAndSync(REGION_CODE, DEAL_YM, HousingType.APT);
    }

    /**
     * 서비스는 집계 구간을 "최근 6개월"로 고정하므로 2015년 데이터로는 검증할 수 없다.
     * 매퍼를 직접 불러 구간을 201512로 지정한다.
     */
    @Test
    void 전세_금액_조회() {
        MedianAggResult agg = rentTransactionMapper.findAggregatedMedian(
                request(DealType.JEONSE, null, null),
                List.of(REGION_CODE),
                AREA_MIN_SQM, AREA_MAX_SQM, DEAL_YM, DEAL_YM);

        System.out.println("전세 조회 건수: " + agg.getSampleCount());
        assertNotNull(agg, "집계 결과가 null입니다.");
        assertTrue(agg.getSampleCount() != null && agg.getSampleCount() > 0, "전세 실거래가 조회되지 않았습니다.");
        assertAll(
                () -> assertTrue(agg.getDepositQ2() != null && agg.getDepositQ2() > 0,
                        "deposit > 0 필터가 동작하지 않았습니다."),
                () -> assertTrue(agg.getRentQ2() == null || agg.getRentQ2() == 0,
                        "전세인데 월세 중앙값이 0이 아닙니다."));
    }

    /** 월세 조건 <if> 분기와 monthly_rent → rentQ2 매핑을 함께 확인한다. */
    @Test
    void 월세_금액_조회() {
        MedianAggResult agg = rentTransactionMapper.findAggregatedMedian(
                request(DealType.WOLSE, MONTHLY_RENT_MIN, MONTHLY_RENT_MAX),
                List.of(REGION_CODE),
                AREA_MIN_SQM, AREA_MAX_SQM, DEAL_YM, DEAL_YM);

        System.out.println("월세 조회 건수: " + agg.getSampleCount());
        assertNotNull(agg, "집계 결과가 null입니다.");
        assertTrue(agg.getSampleCount() != null && agg.getSampleCount() > 0, "월세 실거래가 조회되지 않았습니다.");
        assertTrue(agg.getRentQ2() != null && agg.getRentQ2() > 0,
                "월세 중앙값이 0 이하입니다. 매핑이나 필터를 확인하세요.");
    }

    /** 없는 지역 코드는 조회 전에 PROPERTY_001로 끊긴다. */
    @Test
    void 존재하지_않는_지역코드() {
        RentMedianRequest request = request(DealType.JEONSE, null, null);
        request.setRegionCode("00000");

        BusinessException e = assertThrows(BusinessException.class,
                () -> rentMedianService.getMedian(request));
        assertEquals(ErrorCode.REGION_NOT_FOUND, e.getErrorCode());
    }

    /**
     * 서비스 전체 경로. 집계 구간이 최근 6개월이라 2015년 데이터는 잡히지 않으므로
     * 건수는 단정하지 않고, 응답 조립과 표본 0건 처리만 확인한다.
     */
    @Test
    void 서비스_응답_조립() {
        RentMedianResponse response = rentMedianService.getMedian(request(DealType.JEONSE, null, null));

        System.out.println("최근 6개월 표본: " + response.getSampleCount()
                + " (" + response.getBaseStartYm() + " ~ " + response.getBaseEndYm() + ")");

        assertAll(
                () -> assertEquals(REGION_CODE, response.getRegionCode()),
                () -> assertNotNull(response.getRegionName(), "region.full_name이 조회되지 않았습니다."),
                () -> assertEquals(6, monthSpan(response), "집계 구간이 6개월이 아닙니다."),
                // 전세 조회라 월세 분위값은 계산하지 않는다.
                () -> assertNull(response.getMonthlyRent().getMedian()),
                () -> assertEquals(response.getSampleCount() == 0,
                        response.getDeposit().getMedian() == null,
                        "표본 유무와 보증금 분위값의 null 여부가 어긋납니다."));
    }

    private RentMedianRequest request(DealType dealType, Long monthlyRentMin, Long monthlyRentMax) {
        RentMedianRequest request = new RentMedianRequest();
        request.setRegionCode(REGION_CODE);
        request.setHousingType(HousingType.APT);
        request.setDealType(dealType);
        request.setAreaMin(1);
        request.setAreaMax(10_000);
        request.setDepositMin(DEPOSIT_MIN);
        request.setDepositMax(DEPOSIT_MAX);
        request.setMonthlyRentMin(monthlyRentMin);
        request.setMonthlyRentMax(monthlyRentMax);
        return request;
    }

    /** baseStartYm ~ baseEndYm이 몇 개 월을 덮는지 (양끝 포함) */
    private int monthSpan(RentMedianResponse response) {
        int start = Integer.parseInt(response.getBaseStartYm());
        int end = Integer.parseInt(response.getBaseEndYm());
        return (end / 100 * 12 + end % 100) - (start / 100 * 12 + start % 100) + 1;
    }
}
