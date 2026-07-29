package com.team.independence.asset.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectedInstitution {
    private Long id;
    private Long connectedAccountId;
    private String institutionCode;  // institution.code FK (= CODEF organization 코드)
    private String status;  // ACTIVE / AUTH_EXPIRED / ERROR
    private LocalDateTime lastSyncedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime lastAttemptedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
