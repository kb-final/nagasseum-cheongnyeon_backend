package com.team.independence.asset.dto.manual;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ManualAssetRequest {
    private String assetType;
    private Long amount;
}
