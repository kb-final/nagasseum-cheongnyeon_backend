package com.team.independence.property.service;

import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;

public interface RentMedianService {

    /**
     * 조건에 부합하는 최근 6개월 실거래의 보증금·월세 4분위값을 조회한다.
     *
     * @throws com.team.independence.common.exception.BusinessException 지역 코드가 존재하지 않으면
     */
    RentMedianResponse getMedian(RentMedianRequest request);
}
