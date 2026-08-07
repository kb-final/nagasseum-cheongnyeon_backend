package com.team.independence.asset.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.summary.AssetSummaryResponse;

public interface AssetSummaryService {
    AssetSummaryResponse getSummary(Long memberId);
    void updateMonthlySavings(Long memberId, Long monthlySavings);
    AssetNetWorthBreakdown getNetWorthBreakdown(Long memberId);
}
