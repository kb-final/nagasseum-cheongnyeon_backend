package com.team.independence.compare.service;

import com.team.independence.compare.dto.CompareResponse;

public interface CompareService {

    /**
     * 나와 비슷한 자산·나이 코호트의 목표 통계를 조회한다.
     *
     * @param memberId   조회 대상 회원
     * @param assetRange 자산 비교 범위(±원). 순자산 기준
     * @param ageRange   나이 비교 범위(±세)
     */
    CompareResponse getComparison(Long memberId, Long assetRange, Integer ageRange);
}