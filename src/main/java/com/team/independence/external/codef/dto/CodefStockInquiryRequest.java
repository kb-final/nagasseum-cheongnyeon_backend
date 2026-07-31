package com.team.independence.external.codef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodefStockInquiryRequest {
    private String organization;
    private String connectedId;
}
