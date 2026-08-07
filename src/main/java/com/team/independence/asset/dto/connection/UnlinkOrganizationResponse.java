package com.team.independence.asset.dto.connection;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnlinkOrganizationResponse {
    private String organizationCode;
    private String organizationName;
}
