package com.team.independence.external.codef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodefBankInquiryRequest {
    private String organization;
    private String connectedId;
    private String birthDate;
    @Builder.Default
    private String withdrawAccountNo = "";
    @Builder.Default
    private String withdrawAccountPassword = "";
}
