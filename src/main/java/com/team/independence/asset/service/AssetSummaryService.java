package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetSummaryResponse;

public interface AssetSummaryService {
    AssetSummaryResponse getSummary(Long memberId);
}
