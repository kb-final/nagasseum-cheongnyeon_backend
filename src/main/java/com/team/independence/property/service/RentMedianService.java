package com.team.independence.property.service;

import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
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
}
