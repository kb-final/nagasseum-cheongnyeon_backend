package com.team.independence.asset.dto.connection;

import com.team.independence.asset.domain.codef.Institution;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OrganizationResponse {
    private String organizationCode;
    private String organizationName;
    private String businessType;
    private List<String> supportedLoginTypes;
    private boolean isConnected;

    public static OrganizationResponse from(Institution institution) {
        return OrganizationResponse.builder()
                .organizationCode(institution.getCode())
                .organizationName(institution.getName())
                .businessType(institution.getBusinessType())
                .supportedLoginTypes(List.of("ID"))
                .isConnected(institution.isConnected())
                .build();
    }
}
