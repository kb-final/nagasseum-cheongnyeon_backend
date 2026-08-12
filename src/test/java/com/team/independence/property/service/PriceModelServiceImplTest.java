package com.team.independence.property.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.MonthlyPricePoint;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PriceModelServiceImpl 서비스 로직 단위 테스트.
 *
 * <p>DB 없이 돈다. Mapper를 Mockito로 목 처리해 반환값을 지정하고,
 * 서비스의 그룹핑·필터링·중앙값 산출 로직만 검증한다.
 * PriceModel.estimate() 수식 자체는 PriceModelTest에서 별도로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PriceModelServiceImplTest {

    private static final String REGION_CODE = "11680";
    private static final String REGION_NAME = "서울특별시 강남구";
    /** 20평 = 20 × 3.305785 ㎡ */
    private static final BigDecimal AREA_20PY = new BigDecimal("66.1157");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Mock private RegionMapper regionMapper;
    @Mock private RentTransactionMapper rentTransactionMapper;

    @InjectMocks private PriceModelServiceImpl service;

    private PriceModelRequest request;

    @BeforeEach
    void setUp() {
        request = new PriceModelRequest();
        request.setRegionCode(REGION_CODE);
        request.setHousingType(HousingType.APT);
        request.setDealType(DealType.JEONSE);
        request.setAreaMin(15);
        request.setAreaMax(25);
    }

    // ------------------------------------------------------------------
    // 예외 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("존재하지 않는 지역코드 → REGION_NOT_FOUND")
    void 존재하지_않는_지역코드() {
        when(regionMapper.findFullNameByCode(REGION_CODE)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.estimate(request));

        assertEquals(ErrorCode.REGION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("유효 월이 6개 미만 → PROPERTY_INSUFFICIENT_DATA")
    void 유효_월_6개_미만_예외() {
        when(regionMapper.findFullNameByCode(REGION_CODE)).thenReturn(REGION_NAME);
        // 5개월치만 5건 이상 → 유효 월 5개, 최소(6) 미달
        when(rentTransactionMapper.findAmountsForPriceModel(
                eq(REGION_CODE), any(), any(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(monthsData(5, 5, 200_000_000L, AREA_20PY));

        BusinessException e = assertThrows(BusinessException.class, () -> service.estimate(request));

        assertEquals(ErrorCode.PROPERTY_INSUFFICIENT_DATA, e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 표본 필터링
    // ------------------------------------------------------------------

    @Test
    @DisplayName("표본 5건 미만 월은 제외되어 유효 월 수에서 빠진다")
    void 표본_5건_미만_월_제외() {
        when(regionMapper.findFullNameByCode(REGION_CODE)).thenReturn(REGION_NAME);

        // 12개월치: 앞 6개월은 표본 4건(미달), 뒤 6개월은 표본 5건(통과)
        List<MonthlyPricePoint> raw = new ArrayList<>();
        raw.addAll(monthsData(6, 4, 200_000_000L, AREA_20PY)); // 필터됨
        raw.addAll(monthsData(6, 5, 200_000_000L, AREA_20PY)); // 유효

        when(rentTransactionMapper.findAmountsForPriceModel(
                eq(REGION_CODE), any(), any(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(raw);

        PriceModelResponse response = service.estimate(request);

        assertEquals(6, response.getMonths(), "앞 6개월이 필터되어 유효 월 수는 6이어야 한다.");
    }

    // ------------------------------------------------------------------
    // 중앙값 산출 (간접 검증)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("모든 달 가격이 동일 → CAGR ≈ 0, σ ≈ 0")
    void 가격_고정_시계열_CAGR_0() {
        when(regionMapper.findFullNameByCode(REGION_CODE)).thenReturn(REGION_NAME);
        // 12개월, 월별 5건, 동일한 deposit·area → 평단가 매달 같음
        when(rentTransactionMapper.findAmountsForPriceModel(
                eq(REGION_CODE), any(), any(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(monthsData(12, 5, 200_000_000L, AREA_20PY));

        PriceModelResponse response = service.estimate(request);

        assertEquals(0.0, response.getCagr(),    0.01, "가격 고정 → CAGR ≈ 0");
        assertEquals(0.0, response.getAnnualVol(), 0.01, "가격 고정 → σ ≈ 0");
    }

    @Test
    @DisplayName("홀수 개 표본 → 중앙값이 정중앙 값을 반환한다")
    void 홀수_표본_중앙값() {
        when(regionMapper.findFullNameByCode(REGION_CODE)).thenReturn(REGION_NAME);

        // 한 달에 5건: 평단가가 각각 다른 deposit으로 만든다.
        // area=AREA_20PY(20평), deposit=1억/2억/3억/4억/5억 → 평단가=500만/1000만/1500만/2000만/2500만
        // 중앙값(3번째) = 1500만/평
        // 12개월 모두 같은 분포 → CAGR ≈ 0
        List<MonthlyPricePoint> raw = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            String ym = YearMonth.now().minusMonths(11 - m).format(YM);
            raw.add(new MonthlyPricePoint(ym, 100_000_000L, AREA_20PY));
            raw.add(new MonthlyPricePoint(ym, 200_000_000L, AREA_20PY));
            raw.add(new MonthlyPricePoint(ym, 300_000_000L, AREA_20PY));
            raw.add(new MonthlyPricePoint(ym, 400_000_000L, AREA_20PY));
            raw.add(new MonthlyPricePoint(ym, 500_000_000L, AREA_20PY));
        }

        when(rentTransactionMapper.findAmountsForPriceModel(
                eq(REGION_CODE), any(), any(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(raw);

        PriceModelResponse response = service.estimate(request);

        // 모든 달 중앙값이 동일 → CAGR ≈ 0
        assertEquals(0.0, response.getCagr(), 0.01);
        assertEquals(12, response.getMonths());
    }

    // ------------------------------------------------------------------
    // 응답 조립
    // ------------------------------------------------------------------

    @Test
    @DisplayName("정상 경로 → 응답 필드가 올바르게 조립된다")
    void 정상_경로_응답_조립() {
        when(regionMapper.findFullNameByCode(REGION_CODE)).thenReturn(REGION_NAME);
        when(rentTransactionMapper.findAmountsForPriceModel(
                eq(REGION_CODE), any(), any(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(monthsData(12, 5, 200_000_000L, AREA_20PY));

        PriceModelResponse response = service.estimate(request);

        assertEquals(REGION_CODE, response.getRegionCode());
        assertEquals(REGION_NAME, response.getRegionName());
        assertEquals(HousingType.APT, response.getHousingType());
        assertEquals(DealType.JEONSE, response.getDealType());
        assertNotNull(response.getStartYm());
        assertNotNull(response.getEndYm());
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    /**
     * months개월치 데이터를 생성한다. 각 달마다 samplesPerMonth건, 동일한 deposit·area.
     * 월 순서는 현재 월 기준 과거로 계산한다.
     */
    private List<MonthlyPricePoint> monthsData(int months, int samplesPerMonth,
                                                long deposit, BigDecimal area) {
        List<MonthlyPricePoint> list = new ArrayList<>();
        for (int m = 0; m < months; m++) {
            String ym = YearMonth.now().minusMonths(months - 1 - m).format(YM);
            for (int s = 0; s < samplesPerMonth; s++) {
                list.add(new MonthlyPricePoint(ym, deposit, area));
            }
        }
        return list;
    }
}
