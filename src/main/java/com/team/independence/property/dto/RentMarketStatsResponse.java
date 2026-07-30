package com.team.independence.property.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RentMarketStatsResponse {
    private Long p25;
    private Long median;
    private Long p75;
    private int sampleCount;
}
