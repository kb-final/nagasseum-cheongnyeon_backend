package com.team.independence.asset.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class LinkedOrganizationResponse {
    private String organizationCode;
    private String organizationName;
    private String businessType;
    private LocalDateTime connectedAt;
}
