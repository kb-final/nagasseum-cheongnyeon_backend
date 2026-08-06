package com.team.independence.external.codef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodefCardInquiryRequest {
    private String organization;
    private String connectedId;
    @Builder.Default
    private String cardNo = "";
    @Builder.Default
    private String cardPassword = "";
    @Builder.Default
    private String birthDate = "";
    @Builder.Default
    private String inquiryType = "1";
}
