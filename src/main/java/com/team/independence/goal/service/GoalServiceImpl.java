package com.team.independence.goal.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.property.service.RegionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ★ 1단계 범위 ★
 * 프론트 희망조건 입력을 검증·정규화하고 region_code까지 붙여 돌려준다.
 * 자산 순자산 조회 / 매물 중앙값 비교 / 목표 저장은 다음 단계에서 추가.
 */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private static final String DEAL_TYPE_JEONSE = "전세";

    private final RegionQueryService regionQueryService;

    @Override
    public GoalDiagnosisResponse diagnose(Long memberId, GoalDiagnosisRequest request) {
        validateRange(request.getAreaMin(), request.getAreaMax());
        validateRange(request.getDepositMin(), request.getDepositMax());

        long monthlyRentMin = normalizeMonthlyRent(request);
        long monthlyRentMax = normalizeMonthlyRentMax(request);
        if (!DEAL_TYPE_JEONSE.equals(request.getDealType())) {
            validateRange(monthlyRentMin, monthlyRentMax);
        }

        String regionCode = regionQueryService.resolveRegionCode(request.getSido(), request.getSigungu());

        return GoalDiagnosisResponse.builder()
                .regionCode(regionCode)
                .sido(request.getSido())
                .sigungu(request.getSigungu())
                .housingType(request.getHousingType())
                .dealType(request.getDealType())
                .areaMin(request.getAreaMin())
                .areaMax(request.getAreaMax())
                .depositMin(request.getDepositMin())
                .depositMax(request.getDepositMax())
                .monthlyRentMin(monthlyRentMin)
                .monthlyRentMax(monthlyRentMax)
                .monthlySaving(request.getMonthlySaving())
                .targetDate(request.getTargetDate())
                .build();
    }

    /** 전세면 월세 입력값과 무관하게 0으로 고정 */
    private long normalizeMonthlyRent(GoalDiagnosisRequest request) {
        if (DEAL_TYPE_JEONSE.equals(request.getDealType())) {
            return 0L;
        }
        return request.getMonthlyRentMin() != null ? request.getMonthlyRentMin() : 0L;
    }

    private long normalizeMonthlyRentMax(GoalDiagnosisRequest request) {
        if (DEAL_TYPE_JEONSE.equals(request.getDealType())) {
            return 0L;
        }
        return request.getMonthlyRentMax() != null ? request.getMonthlyRentMax() : 0L;
    }

    private void validateRange(int min, int max) {
        if (min > max) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_CONDITION);
        }
    }

    private void validateRange(long min, long max) {
        if (min > max) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_CONDITION);
        }
    }
}
