package com.team.independence.property.service;

import com.team.independence.property.dto.RentMarketStatsResponse;
import java.math.BigDecimal;

public interface RentMarketQueryService {

    /** 조건에 맞는 실거래 보증금의 25%/중앙값/75% 백분위수. 데이터가 없으면 세 값 다 null, sampleCount=0. */
    RentMarketStatsResponse getMarketStats(String regionCode, String housingType, String dealType,
                                            BigDecimal areaMinSqm, BigDecimal areaMaxSqm);
}
