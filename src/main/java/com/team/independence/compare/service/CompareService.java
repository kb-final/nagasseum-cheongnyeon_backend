package com.team.independence.compare.service;

import com.team.independence.compare.dto.AssetCompareResponse;
import com.team.independence.compare.dto.CompareRequest;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.dto.GoalCompareResponse;

public interface CompareService {

    /** @deprecated 탭 분리 전 구버전. /assets, /goals 엔드포인트로 대체 예정 */
    @Deprecated
    CompareResponse getComparison(Long memberId, Long assetRange, Integer ageRange);

    AssetCompareResponse getAssetComparison(Long memberId, CompareRequest request);

    GoalCompareResponse getGoalComparison(Long memberId, CompareRequest request);
}