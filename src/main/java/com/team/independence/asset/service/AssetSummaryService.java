package com.team.independence.asset.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.summary.AssetSummaryResponse;

public interface AssetSummaryService {
    AssetSummaryResponse getSummary(Long memberId);
    void updateMonthlySavings(Long memberId, Long monthlySavings);
    AssetNetWorthBreakdown getNetWorthBreakdown(Long memberId);
    /** monthlySavings가 미등록(null)이면 0을 반환한다. 자산 요약 자체가 없으면 예외. */
    long getMonthlySavingsOrZero(Long memberId);
}
