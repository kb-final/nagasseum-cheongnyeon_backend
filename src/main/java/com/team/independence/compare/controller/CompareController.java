package com.team.independence.compare.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import com.team.independence.compare.dto.AssetCompareResponse;
import com.team.independence.compare.dto.CompareRequest;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.dto.GoalCompareResponse;
import com.team.independence.compare.service.CompareService;

import lombok.RequiredArgsConstructor;

/**
 * 또래 비교 API.
 */
@RestController
@RequestMapping("/api/v1/comparison")
@RequiredArgsConstructor
public class CompareController {

    private static final long DEFAULT_ASSET_RANGE = 10_000_000L;
    private static final int DEFAULT_AGE_RANGE = 2;

    private final CompareService compareService;

    /** @deprecated 탭 분리 전 구버전. /assets, /goals 엔드포인트로 대체 예정 */
    @Deprecated
    @GetMapping
    public ApiResponse<CompareResponse> getComparison(
            @LoginMember Long memberId,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_ASSET_RANGE) Long assetRange,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_AGE_RANGE) Integer ageRange) {
        return ApiResponse.ok(compareService.getComparison(memberId, assetRange, ageRange));
    }

    @GetMapping("/assets")
    public ApiResponse<AssetCompareResponse> getAssetComparison(
            @LoginMember Long memberId,
            @ModelAttribute CompareRequest request) {
        return ApiResponse.ok(compareService.getAssetComparison(memberId, request));
    }

    @GetMapping("/goals")
    public ApiResponse<GoalCompareResponse> getGoalComparison(
            @LoginMember Long memberId,
            @ModelAttribute CompareRequest request) {
        return ApiResponse.ok(compareService.getGoalComparison(memberId, request));
    }
}
