package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import lombok.Data;

/** findBulkMedian 쿼리 결과 DTO — (주거유형, 거래유형, 평수 버킷)별 분위값 1행. */
@Data
public class BulkMedianResult {
    private HousingType housingType;
    private DealType dealType;
    private int areaMin;
    private int areaMax;
    private Long depositQ1;
    private Long depositQ2;
    private Long depositQ3;
    private Long rentQ1;
    private Long rentQ2;
    private Long rentQ3;
    private int sampleCount;
}
