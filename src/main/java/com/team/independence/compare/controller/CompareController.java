package com.team.independence.compare.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.service.CompareService;

import lombok.RequiredArgsConstructor;

/**
 * 또래 비교 API.
 */
@RestController
@RequestMapping("/api/v1/comparison")
@RequiredArgsConstructor
public class CompareController {

    /** 기본 비교 범위. 쿼리 파라미터가 없으면 이 값을 쓴다 */
    private static final long DEFAULT_ASSET_RANGE = 10_000_000L;
    private static final int DEFAULT_AGE_RANGE = 2;

    private final CompareService compareService;

    /**
     * 내 또래 비교 결과.
     *
     * <p>memberId는 요청에서 받지 않는다. AuthInterceptor가 토큰을 검증하고 넣어둔 값을
     * {@code @LoginMember}가 꺼내온다. 파라미터로 받으면 남의 결과를 조회할 수 있다.
     */
    @GetMapping
    public ApiResponse<CompareResponse> getComparison(
            @LoginMember Long memberId,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_ASSET_RANGE) Long assetRange,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_AGE_RANGE) Integer ageRange) {
        return ApiResponse.ok(compareService.getComparison(memberId, assetRange, ageRange));
    }
}