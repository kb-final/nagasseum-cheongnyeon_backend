package com.team.independence.asset.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetSummary {
    private Long id;
    private Long memberId;
    private Long totalAssets;
    private Long loanBalance;
    private Long monthlySavings;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
