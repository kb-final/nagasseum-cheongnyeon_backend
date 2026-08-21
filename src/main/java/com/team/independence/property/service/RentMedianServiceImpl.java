package com.team.independence.property.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.BulkMedianResult;
import com.team.independence.property.dto.MedianAggResult;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.RentMedianResponse.Quartile;
import com.team.independence.property.dto.SigunguMedianResult;
import com.team.independence.property.mapper.RegionMapper;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentMedianServiceImpl implements RentMedianService {

    /** 집계 구간 길이. 조회 시점 월을 포함해 6개월 */
    private static final int MONTHS = 6;

    /** 평 → ㎡ 환산 계수 (1평 = 3.305785㎡) */
    private static final double PYEONG_TO_SQM = 3.305785;

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final RegionMapper regionMapper;
    private final RentTransactionMapper rentTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public RentMedianResponse getMedian(RentMedianRequest request) {
        String regionName = resolveRegionName(request.getRegionCode());
        if (regionName == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }

        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(MONTHS - 1);
        String startYm = start.format(YM);
        String endYm = end.format(YM);

        MedianAggResult agg = rentTransactionMapper.findAggregatedMedian(
                request,
                toSqm(request.getAreaMin()),
                toSqm(request.getAreaMax()),
                startYm,
                endYm);

        if (agg == null || agg.getSampleCount() == null || agg.getSampleCount() == 0) {
            return emptyResponse(request.getRegionCode(), regionName, request, startYm, endYm);
        }

        return RentMedianResponse.builder()
                .regionCode(request.getRegionCode())
                .regionName(regionName)
                .housingType(request.getHousingType())
                .dealType(request.getDealType())
                .baseStartYm(startYm)
                .baseEndYm(endYm)
                .sampleCount(agg.getSampleCount())
                .deposit(Quartile.of(agg.getDepositQ1(), agg.getDepositQ2(), agg.getDepositQ3()))
                .monthlyRent(request.usesMonthlyRentFilter() && agg.getRentQ2() != null
                        ? Quartile.of(agg.getRentQ1(), agg.getRentQ2(), agg.getRentQ3())
                        : Quartile.empty())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, RentMedianResponse> getBulkMedian(String regionCode, String startYm, String endYm) {
        String regionName = resolveRegionName(regionCode);
        if (regionName == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }

        // (주거유형, 거래유형) 조합별로 개별 쿼리를 실행해 idx_rent_query 인덱스를 탄다.
        // 단일 bulk 쿼리 대비 처리 행 수가 1/8로 줄어 윈도우 함수 비용이 크게 감소한다.
        Map<String, RentMedianResponse> result = new HashMap<>();
        for (HousingType housingType : HousingType.values()) {
            for (DealType dealType : DealType.values()) {
                List<BulkMedianResult> rows = rentTransactionMapper.findBulkMedianByType(
                        regionCode, housingType, dealType, startYm, endYm);
                for (BulkMedianResult r : rows) {
                    String key = housingType + "|" + dealType + "|" + r.getAreaMin();
                    RentMedianResponse response = RentMedianResponse.builder()
                            .regionCode(regionCode)
                            .regionName(regionName)
                            .housingType(housingType)
                            .dealType(dealType)
                            .baseStartYm(startYm)
                            .baseEndYm(endYm)
                            .sampleCount(r.getSampleCount())
                            .deposit(Quartile.of(r.getDepositQ1(), r.getDepositQ2(), r.getDepositQ3()))
                            .monthlyRent(r.getRentQ2() != null
                                    ? Quartile.of(r.getRentQ1(), r.getRentQ2(), r.getRentQ3())
                                    : Quartile.empty())
                            .build();
                    result.put(key, response);
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, SigunguMedianResult> getMediansByRegionCodes(
            List<String> sigunguCodes, HousingType housingType, DealType dealType,
            int areaMin, int areaMax,
            long depositMin, long depositMax,
            Long monthlyRentMin, Long monthlyRentMax) {

        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(MONTHS - 1);

        List<SigunguMedianResult> rows = rentTransactionMapper.findMediansByRegionCodes(
                sigunguCodes, housingType, dealType,
                toSqm(areaMin), toSqm(areaMax),
                start.format(YM), end.format(YM),
                depositMin, depositMax,
                monthlyRentMin, monthlyRentMax);

        Map<String, SigunguMedianResult> result = new HashMap<>();
        for (SigunguMedianResult row : rows) {
            result.put(row.getRegionCode(), row);
        }
        return result;
    }

    /** 시도 코드 길이(법정동코드 앞 2자리). 이보다 길면 시군구 코드다. */
    private static final int SIDO_CODE_LENGTH = 2;

    private String resolveRegionName(String regionCode) {
        if (regionCode != null && regionCode.length() == SIDO_CODE_LENGTH) {
            return regionMapper.findSidoNameByPrefix(regionCode);
        }
        return regionMapper.findFullNameByCode(regionCode);
    }

    private long toSqm(int pyeong) {
        return (long) Math.floor(pyeong * PYEONG_TO_SQM);
    }

    private RentMedianResponse emptyResponse(String regionCode, String regionName,
            RentMedianRequest request, String startYm, String endYm) {
        return RentMedianResponse.builder()
                .regionCode(regionCode)
                .regionName(regionName)
                .housingType(request.getHousingType())
                .dealType(request.getDealType())
                .baseStartYm(startYm)
                .baseEndYm(endYm)
                .sampleCount(0)
                .deposit(Quartile.empty())
                .monthlyRent(Quartile.empty())
                .build();
    }
}
