package com.team.independence.compare.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.service.CompareService;

import lombok.RequiredArgsConstructor;

/**
 * 또래 비교 API.
 *
 * <p>비교 도메인이지만 경로는 /api/v1/goals 아래다(명세서 기준). /api/v1/compare 아님.
 */
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class CompareController {

    /** 기본 비교 범위. 쿼리 파라미터가 없으면 이 값을 쓴다 */
    private static final long DEFAULT_ASSET_RANGE = 10_000_000L;
    private static final int DEFAULT_AGE_RANGE = 2;

    /** 인증 전 임시 회원. 프론트가 memberId를 안 보내므로 기본값으로 받는다 */
    private static final long TEMP_MEMBER_ID = 1L;

    private final CompareService compareService;

    // TODO: 인증 붙으면 memberId를 @RequestParam 대신 토큰에서 꺼내고 defaultValue를 지운다
    @GetMapping("/comparison")
    public ApiResponse<CompareResponse> getComparison(
            @RequestParam(required = false, defaultValue = "" + TEMP_MEMBER_ID) Long memberId,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_ASSET_RANGE) Long assetRange,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_AGE_RANGE) Integer ageRange) {
        return ApiResponse.ok(compareService.getComparison(memberId, assetRange, ageRange));
    }
}