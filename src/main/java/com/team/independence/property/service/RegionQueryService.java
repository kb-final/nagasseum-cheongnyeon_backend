package com.team.independence.property.service;

public interface RegionQueryService {

    /** 시/도 + 군/구 이름으로 region_code를 조회한다. 없으면 BusinessException(REGION_NOT_FOUND). */
    String resolveRegionCode(String sido, String sigungu);
}
