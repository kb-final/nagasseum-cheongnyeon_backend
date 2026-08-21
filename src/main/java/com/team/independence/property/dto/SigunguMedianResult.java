package com.team.independence.property.dto;

import lombok.Data;

/** findMediansBySidoPrefix 결과 DTO - 시군구별 보증금, 월세 중앙값 1행 */
@Data
public class SigunguMedianResult {
    private String regionCode;
    private Long depositMedian;
    private Long rentMedian;
    private int sampleCount;
}
