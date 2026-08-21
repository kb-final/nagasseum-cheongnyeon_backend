package com.team.independence.property.service;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.dto.SigunguMedianResult;
import java.util.Map;

public interface RentMedianService {

    /**
     * 조건에 부합하는 최근 6개월 실거래의 보증금·월세 4분위값을 조회한다.
     *
     * @throws com.team.independence.common.exception.BusinessException 지역 코드가 존재하지 않으면
     */
    RentMedianResponse getMedian(RentMedianRequest request);

    /**
     * HoldOut 알고리즘 전용. 지역·기간만 받아 (주거유형, 거래유형, 평수 버킷)별 분위값을
     * 한 번의 쿼리로 조회한다.
     *
     * @return 키: "{HousingType}|{DealType}|{areaMin평}" 형식의 맵
     * @throws com.team.independence.common.exception.BusinessException 지역 코드가 존재하지 않으면
     */
    Map<String, RentMedianResponse> getBulkMedian(String regionCode, String startYm, String endYm);

    /**
     * RealisticAlgorithm.selectRegion() 전용. 시도 내 모든 시군구의 보증금 및 월세 중앙값을
     * 단일 쿼리로 반환해 N번 개별 조회를 대체한다.
     *
     * @return 키: 시군구 region_code (5자리)
     */
    Map<String, SigunguMedianResult> getMediansBySidoPrefix(
            String sidoPrefix, HousingType housingType, DealType dealType,
            int areaMin, int areaMax,
            long depositMin, long depositMax,
            Long monthlyRentMin, Long monthlyRentMax);
}
