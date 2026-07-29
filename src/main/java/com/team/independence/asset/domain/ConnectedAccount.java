package com.team.independence.asset.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectedAccount {
    private Long id;
    private Long memberId;
    private String connectedId;  // AES 암호화된 Connected ID
    private String connectedStatus;  // ACTIVE / EXPIRED / REVOKED
    private LocalDateTime lastSyncedAt;
    private String syncStatus;  // SUCCESS / FAILED / IN_PROGRESS
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
