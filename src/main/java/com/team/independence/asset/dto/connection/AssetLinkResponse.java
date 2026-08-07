package com.team.independence.asset.dto.connection;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetLinkResponse {
    private String connectedId;
    private String organization;
    private String action;  // "CREATED" | "ADDED"
}
