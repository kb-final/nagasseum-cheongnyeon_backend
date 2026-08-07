package com.team.independence.asset.dto.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AssetAccountDetailItem {
    private String institutionName;
    private String accountType;
    private String assetCategory;
    private String accountDisplay;
    private String productName;
    private Long currentValue;
    private Long valuationAmount;
    private Long depositReceived;
    private Long valuationPl;
    private Long purchaseAmount;
    private BigDecimal earningsRate;
    private LocalDate startDate;
    private LocalDate maturityDate;
}
