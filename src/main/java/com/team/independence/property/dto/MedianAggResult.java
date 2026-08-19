package com.team.independence.property.dto;

import lombok.Data;

/** findAggregatedMedian 쿼리 결과 DTO — SQL 윈도우 함수로 계산한 분위값 1행. */
@Data
public class MedianAggResult {
    private Long depositQ1;
    private Long depositQ2;
    private Long depositQ3;
    private Long rentQ1;
    private Long rentQ2;
    private Long rentQ3;
    /** 집계에 사용된 실거래 건수. 해당 조건의 데이터가 없으면 null. */
    private Integer sampleCount;
}
