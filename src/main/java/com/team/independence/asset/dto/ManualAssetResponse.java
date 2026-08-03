package com.team.independence.asset.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ManualAssetResponse {
    private Long id;
    private String assetType;
    private Long amount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
