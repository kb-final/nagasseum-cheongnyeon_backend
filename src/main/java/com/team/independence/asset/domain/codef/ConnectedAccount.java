package com.team.independence.asset.domain.codef;

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
    private String birthDate;  // 생년월일 YYMMDD (계좌 조회 API 재사용)
    private String connectedStatus;  // ACTIVE / EXPIRED / REVOKED
    private LocalDateTime lastSyncedAt;
    private String syncStatus;  // SUCCESS / FAILED / IN_PROGRESS
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
