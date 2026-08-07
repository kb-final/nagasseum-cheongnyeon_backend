package com.team.independence.asset.domain.manual;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualAsset {
    private Long id;
    private Long memberId;
    private String assetType;  // DEPOSIT: 현재 거주 보증금
    private Long amount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
