package com.team.independence.asset.dto.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AssetAccountQueryItem {
    private Long id;
    private String institutionName;
    private String accountType;
    private String assetCategory;
    private String productName;
    private String accountDisplay;
    private Long currentValue;
    private Long valuationAmount;
    private Long depositReceived;
}
