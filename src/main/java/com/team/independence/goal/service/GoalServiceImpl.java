package com.team.independence.goal.service;

import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.dto.GoalCreateRequest;
import com.team.independence.goal.dto.GoalDiagnosisRequest;
import com.team.independence.goal.dto.GoalDiagnosisResponse;
import com.team.independence.goal.dto.GoalResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.mapper.GoalMapper;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.service.RegionQueryService;
import com.team.independence.property.service.RentMedianService;
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프론트 희망조건 입력을 검증·정규화하고 region_code까지 붙여 돌려준다.
 * 순자산 중 예적금만 연 5% 복리로 굴리고(나머지는 인정률만 반영한 원금 그대로) + 월저축액 예상값으로
 * budget을, 조건에 맞는 실거래 보증금 4분위값을 marketStats로 계산해 반환.
 * budget과 median을 비교해 status/shortfall을 매기고, 부족(INSUFFICIENT)할 때
 * 저축액 증가/기간 연장/평수 축소 3가지 조정 제안을 함께 계산한다.
 * 목표 저장은 다음 단계에서 추가.
 */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private static final String STATUS_ACHIEVABLE = "ACHIEVABLE";
    private static final String STATUS_INSUFFICIENT = "INSUFFICIENT";

    private static final String GOAL_TYPE_HOUSING = "HOUSING";
    private static final String GOAL_STATUS_ACTIVE = "ACTIVE";

    /** 예산 계산에 적용하는 연 이자율(고정 상수). 실제 상품 금리 연동 없이 5%로 가정. */
    private static final double ANNUAL_INTEREST_RATE = 0.05;

    /** 기간 연장 제안 탐색 상한(개월) */
    private static final long EXTEND_PERIOD_MAX_MONTHS = 240;
    /** 평수 축소 제안 탐색 상한(평) */
    private static final int REDUCE_SIZE_MAX_STEPS = 10;
    /** 예상 달성 시점 탐색 상한(개월). 저축액이 미미해 사실상 도달 불가할 때 무한 루프를 막는 안전장치. */
    private static final long MAX_FORECAST_MONTHS = 1200;

    private final RegionQueryService regionQueryService;
    private final AssetService assetService; // 자산 정보 조회
    private final RentMedianService rentMedianService; // 조건에 맞는 실거래 4분위값 조회
    private final GoalMapper goalMapper;
    private final GoalHousingMapper goalHousingMapper;

    @Override
    public GoalDiagnosisResponse diagnose(Long memberId, GoalDiagnosisRequest request) {
        // 희망 조건 범위 검증
        validateMonthlySavings(request.getMonthlySavings());
        validateRange(request.getSizeMin(), request.getSizeMax());
        validateRange(request.getDepositMin(), request.getDepositMax());
        validateTargetDate(request.getTargetDate());

        HousingType housingType = HousingType.valueOf(request.getPropertyType());
        DealType dealType = DealType.valueOf(request.getTradeType());
        validateMonthlyRentRequired(dealType, request.getMonthlyRentMax());

        /*
        * 월세 범위 정규화
        *
        * 월세면 입력된 월세 값을 사용하고, null이면 0으로 처리
        * */
        long monthlyRentMin = normalizeMonthlyRent(dealType, request.getMonthlyRentMin());
        long monthlyRentMax = normalizeMonthlyRent(dealType, request.getMonthlyRentMax());
        if (dealType == DealType.WOLSE) {
            validateRange(monthlyRentMin, monthlyRentMax);
        }

        // 지역 코드로 변경
        String regionCode = regionQueryService.resolveRegionCode(
                request.getRegion().getSido(), request.getRegion().getSigungu());

        // 자산 연동 여부 확인 후 순 자산 구성 정보 조회
        assetService.validateConnectedAccountExists(memberId);
        AssetNetWorthBreakdown netWorth = assetService.getNetWorthBreakdown(memberId);
        long interestBearingAssets = netWorth.getInterestBearingAssets(); // 목표 시점까지 이자를 적용할 자산 (예적금)
        long flatRecognizedAssets = netWorth.getFlatRecognizedAssets(); // 인정 금액만 반영할 자산

        long months = monthsUntil(request.getTargetDate()); // 현재 시점부터 목표 시점까지 남은 개월 수 계산

        // 목표 시점의 예상 인정 자산을 계산
        long recognizedAssets = calculateGrownAmount(interestBearingAssets, months) + flatRecognizedAssets;
        long projectedSavings = calculateProjectedSavings(request.getMonthlySavings(), months);
        long totalBudget = recognizedAssets + projectedSavings;

        // 사용자 희망 조건에 맞는 실거래 4분위값을 조회
        RentMedianResponse marketStats = rentMedianService.getMedian(buildMedianRequest(
                regionCode, housingType, dealType,
                request.getSizeMin(), request.getSizeMax(),
                request.getDepositMin(), request.getDepositMax(),
                request.getMonthlyRentMin(), request.getMonthlyRentMax()));

        if (marketStats.getSampleCount() == 0) {
            throw new BusinessException(ErrorCode.GOAL_NO_MARKET_DATA);
        }

        // 총 예산과 중앙값을 비교해 목표 달성 상태를 결정
        String status = determineStatus(marketStats, totalBudget);

        // 예산이 부족한 경우에만 부족 금액 계산
        Long shortfall = STATUS_INSUFFICIENT.equals(status)
                ? marketStats.getDeposit().getMedian() - totalBudget
                : null;

        // 예산이 부족한 경우 조정 제안 생성
        GoalDiagnosisResponse.AdjustmentSuggestions adjustmentSuggestions = null;
        if (STATUS_INSUFFICIENT.equals(status)) {
            long median = marketStats.getDeposit().getMedian();
            adjustmentSuggestions = GoalDiagnosisResponse.AdjustmentSuggestions.builder()
                    .increaseSavings(calculateIncreaseSavingsSuggestion(
                            median, recognizedAssets, request.getMonthlySavings(), months))
                    .extendPeriod(calculateExtendPeriodSuggestion(
                            interestBearingAssets, flatRecognizedAssets, request.getMonthlySavings(), months,
                            median, request.getTargetDate()))
                    .reduceSize(calculateReduceSizeSuggestion(
                            regionCode, housingType, dealType,
                            request.getDepositMin(), request.getDepositMax(),
                            request.getMonthlyRentMin(), request.getMonthlyRentMax(),
                            request.getSizeMin(), request.getSizeMax(), totalBudget))
                    .build();
        }

        return GoalDiagnosisResponse.builder()
                .regionCode(regionCode)
                .region(GoalDiagnosisResponse.RegionInfo.builder()
                        .sido(request.getRegion().getSido())
                        .sigungu(request.getRegion().getSigungu())
                        .build())
                .propertyType(request.getPropertyType())
                .tradeType(request.getTradeType())
                .sizeMin(request.getSizeMin())
                .sizeMax(request.getSizeMax())
                .depositMin(request.getDepositMin())
                .depositMax(request.getDepositMax())
                .monthlyRentMin(monthlyRentMin)
                .monthlyRentMax(monthlyRentMax)
                .monthlySavings(request.getMonthlySavings())
                .targetDate(request.getTargetDate())
                .budget(GoalDiagnosisResponse.BudgetResult.builder()
                        .totalBudget(totalBudget)
                        .recognizedAssets(recognizedAssets)
                        .projectedSavings(projectedSavings)
                        .build())
                .marketStats(GoalDiagnosisResponse.MarketStats.builder()
                        .p25(marketStats.getDeposit().getQ1())
                        .median(marketStats.getDeposit().getMedian())
                        .p75(marketStats.getDeposit().getQ3())
                        .sampleCount(marketStats.getSampleCount())
                        .build())
                .status(status)
                .shortfall(shortfall)
                .adjustmentSuggestions(adjustmentSuggestions)
                .build();
    }

    @Override
    @Transactional
    public GoalResponse createGoal(Long memberId, GoalCreateRequest request) {
        // 희망 조건 범위 검증(진단과 동일 규칙)
        validateMonthlySavings(request.getMonthlySavings());
        validateRange(request.getSizeMin(), request.getSizeMax());
        validateRange(request.getDepositMin(), request.getDepositMax());
        validateTargetDate(request.getTargetDate());

        HousingType housingType = HousingType.valueOf(request.getPropertyType());
        DealType dealType = DealType.valueOf(request.getTradeType());
        validateMonthlyRentRequired(dealType, request.getMonthlyRentMax());

        long monthlyRentMin = normalizeMonthlyRent(dealType, request.getMonthlyRentMin());
        long monthlyRentMax = normalizeMonthlyRent(dealType, request.getMonthlyRentMax());
        if (dealType == DealType.WOLSE) {
            validateRange(monthlyRentMin, monthlyRentMax);
        }

        assetService.validateConnectedAccountExists(memberId);

        // 동시 ACTIVE 목표는 1개만 허용 — 이미 있으면 저장을 거부(수정/삭제 후 재시도 유도)
        if (goalMapper.existsActiveByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.GOAL_ALREADY_EXISTS);
        }

        Goal goal = Goal.builder()
                .memberId(memberId)
                .goalType(GOAL_TYPE_HOUSING)
                .targetAmount(request.getTargetAmount())
                .targetRentMiddleAmount(request.getTargetRentMiddleAmount())
                .targetDate(request.getTargetDate().atDay(1))
                .monthlySaving(request.getMonthlySavings())
                .status(GOAL_STATUS_ACTIVE)
                .build();
        goalMapper.insert(goal);

        GoalHousing goalHousing = GoalHousing.builder()
                .goalId(goal.getId())
                .regionCode(request.getRegionCode())
                .housingType(housingType)
                .dealType(dealType)
                .areaMin(request.getSizeMin())
                .areaMax(request.getSizeMax())
                .depositMin(request.getDepositMin())
                .depositMax(request.getDepositMax())
                .monthlyRentMin(monthlyRentMin)
                .monthlyRentMax(monthlyRentMax)
                .build();
        goalHousingMapper.insert(goalHousing);

        return GoalResponse.builder()
                .goalId(goal.getId())
                .status(goal.getStatus())
                .regionCode(request.getRegionCode())
                .propertyType(request.getPropertyType())
                .tradeType(request.getTradeType())
                .sizeMin(request.getSizeMin())
                .sizeMax(request.getSizeMax())
                .depositMin(request.getDepositMin())
                .depositMax(request.getDepositMax())
                .monthlyRentMin(monthlyRentMin)
                .monthlyRentMax(monthlyRentMax)
                .monthlySavings(request.getMonthlySavings())
                .targetDate(request.getTargetDate())
                .targetAmount(request.getTargetAmount())
                .targetRentMiddleAmount(request.getTargetRentMiddleAmount())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    public Goal findOwnedGoal(Long memberId, Long goalId) {
        Goal goal = goalMapper.findById(goalId);
        if (goal == null) {
            throw new BusinessException(ErrorCode.GOAL_NOT_FOUND);
        }
        if (!goal.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.GOAL_FORBIDDEN);
        }
        return goal;
    }

    /**
     * 목표 달성 상세 조회의 예상 달성 시점 계산에 쓴다.
     * 진단의 기간 연장 제안(calculateExtendPeriodSuggestion)과 같은 방식으로,
     * 저축액을 고정한 채 개월수를 늘려가며 예산이 목표 금액에 닿는 첫 시점을 찾는다.
     * 화면에 필요한 개월수를 그대로 보여줘야 해서 진단의 240개월 상한 대신 안전장치 상한만 둔다.
     */
    @Override
    public Long calculateMonthToReach(AssetNetWorthBreakdown netWorth, long monthlySaving, long targetAmount) {
        long growingAssets = netWorth.getInterestBearingAssets(); // 이자로 불어나는 자산(예적금)
        long fixedAssets = netWorth.getFlatRecognizedAssets();    // 원금 그대로 인정하는 자산

        if (growingAssets + fixedAssets >= targetAmount) {
            return 0L;
        }
        if (monthlySaving <= 0) {
            return null;
        }

        for (long months = 1; months <= MAX_FORECAST_MONTHS; months++) {
            long expectedBudget = calculateGrownAmount(growingAssets, months) + fixedAssets
                    + calculateProjectedSavings(monthlySaving, months);
            if (expectedBudget >= targetAmount) {
                return months;
            }
        }
        return null;
    }

    /** RentMedianService 호출용 요청 조립. sizeMin/sizeMax는 평 단위 그대로 넘기면 내부에서 ㎡로 환산한다. */
    private RentMedianRequest buildMedianRequest(String regionCode, HousingType housingType, DealType dealType,
            int sizeMin, int sizeMax, long depositMin, long depositMax,
            Long monthlyRentMin, Long monthlyRentMax) {
        RentMedianRequest medianRequest = new RentMedianRequest();
        medianRequest.setRegionCode(regionCode);
        medianRequest.setHousingType(housingType);
        medianRequest.setDealType(dealType);
        medianRequest.setAreaMin(sizeMin);
        medianRequest.setAreaMax(sizeMax);
        medianRequest.setDepositMin(depositMin);
        medianRequest.setDepositMax(depositMax);
        medianRequest.setMonthlyRentMin(monthlyRentMin);
        medianRequest.setMonthlyRentMax(monthlyRentMax);
        return medianRequest;
    }

    /** 같은 개월수 기준, budget이 median에 도달하도록 월저축액을 역산 */
    private GoalDiagnosisResponse.IncreaseSavingsSuggestion calculateIncreaseSavingsSuggestion(
            long median, long recognizedAssets, long monthlySavings, long months) {
        if (months == 0) {
            return null;
        }

        // 연 5% 이자율을 월 복리 이자율로 변환
        double monthlyRate = monthlyInterestRate();

        // 미래 가치 계수
        double annuityFactor = (Math.pow(1 + monthlyRate, months) - 1) / monthlyRate;

        // 현재 인정 자산을 제외하고 저축으로 추가 확보해야 하는 금액
        long requiredSavings = median - recognizedAssets;

        // 필요한 월 저축액 역산
        long adjustedMonthlySavings = Math.round(requiredSavings / annuityFactor);

        return GoalDiagnosisResponse.IncreaseSavingsSuggestion.builder()
                .additionalMonthlySavings(adjustedMonthlySavings - monthlySavings) // 현재보다 매달 얼마를 더 저축해야 하는지
                .adjustedMonthlySavings(adjustedMonthlySavings) // 조정 후 필요한 전체 월 저축액
                .build();
    }

    /** 월저축액 고정, budget이 median에 도달하는 최소 개월수를 탐색(최대 EXTEND_PERIOD_MAX_MONTHS).
     *  interestBearingAssets(예적금)만 개월수에 따라 다시 복리 성장시키고, flatRecognizedAssets는 그대로 더한다. */
    private GoalDiagnosisResponse.ExtendPeriodSuggestion calculateExtendPeriodSuggestion(
            long interestBearingAssets, long flatRecognizedAssets, long monthlySavings, long months,
            long median, YearMonth targetDate) {

        // 현재 목표 기간보다 한 달 긴 시점부터 검사
        for (long n = months + 1; n <= EXTEND_PERIOD_MAX_MONTHS; n++) {
            // 연장된 기간을 기준으로 자산의 미래 가치를 다시 계산
            long recognizedAssetsAtN = calculateGrownAmount(interestBearingAssets, n) + flatRecognizedAssets;
            long projected = recognizedAssetsAtN + calculateProjectedSavings(monthlySavings, n);

            // 예상 총예산이 중앙값 이상이 되는 첫번째 시점을 찾으면 반환
            if (projected >= median) {
                long additionalMonths = n - months;
                return GoalDiagnosisResponse.ExtendPeriodSuggestion.builder()
                        .additionalMonths(additionalMonths)
                        .adjustedTargetDate(targetDate.plusMonths(additionalMonths))
                        .build();
            }
        }
        return null;
    }

    /** sizeMin은 고정, sizeMax만 1평씩 줄여가며 median이 budget 이내로 들어오는 첫 지점을 탐색(최대 REDUCE_SIZE_MAX_STEPS평) */
    private GoalDiagnosisResponse.ReduceSizeSuggestion calculateReduceSizeSuggestion(
            String regionCode, HousingType housingType, DealType dealType,
            long depositMin, long depositMax, Long monthlyRentMin, Long monthlyRentMax,
            int sizeMin, int sizeMax, long totalBudget) {

        // 최대 10평까지 줄이되, 최대 평수가 최소 평수보다 작아지지는 않게 함
        int maxSteps = Math.min(REDUCE_SIZE_MAX_STEPS, sizeMax - sizeMin);

        // 최대 희망 평수를 1평씩 줄이면 실거래 통계를 다시 조회
        for (int step = 1; step <= maxSteps; step++) {
            int candidateSizeMax = sizeMax - step;
            RentMedianResponse stats = rentMedianService.getMedian(buildMedianRequest(
                    regionCode, housingType, dealType, sizeMin, candidateSizeMax,
                    depositMin, depositMax, monthlyRentMin, monthlyRentMax));

            // 실거래 데이터가 존재하고 해당 평수 범위의 중앙값이 현재 예산 이하면 해당 평수를 반환
            if (stats.getSampleCount() > 0 && stats.getDeposit().getMedian() <= totalBudget) {
                return GoalDiagnosisResponse.ReduceSizeSuggestion.builder()
                        .deltaSizeMax(candidateSizeMax - sizeMax)
                        .newSizeMax(candidateSizeMax)
                        .build();
            }
        }
        return null;
    }

    /** budget이 중앙값 이상이면 ACHIEVABLE, 아니면 INSUFFICIENT (데이터 없는 경우는 GOAL_NO_MARKET_DATA로 이미 걸러짐) */
    private String determineStatus(RentMedianResponse marketStats, long totalBudget) {
        return totalBudget >= marketStats.getDeposit().getMedian() ? STATUS_ACHIEVABLE : STATUS_INSUFFICIENT;
    }

    /** 목표시점까지 남은 개월수. 이미 지난 달이면 0으로 clamp. */
    private long monthsUntil(YearMonth targetDate) {
        long months = YearMonth.now().until(targetDate, java.time.temporal.ChronoUnit.MONTHS);
        return Math.max(months, 0);
    }

    /** 연 이자율을 복리 기준 월 이자율로 환산: (1+연이자율)^(1/12) - 1 */
    private double monthlyInterestRate() {
        return Math.pow(1 + ANNUAL_INTEREST_RATE, 1.0 / 12) - 1;
    }

    /** 원금이 목표시점까지 복리로 불어난 값(거치식). 예적금(interestBearingAssets)에만 적용한다. */
    private long calculateGrownAmount(long principal, long months) {
        if (months == 0) {
            return principal;
        }
        double monthlyRate = monthlyInterestRate();
        return Math.round(principal * Math.pow(1 + monthlyRate, months));
    }

    /** 매달 monthlySavings씩 넣는 적금이 목표시점까지 복리로 불어난 값(적립식 미래가치) */
    private long calculateProjectedSavings(long monthlySavings, long months) {
        if (months == 0) {
            return 0L;
        }
        double monthlyRate = monthlyInterestRate();
        double futureValueFactor = (Math.pow(1 + monthlyRate, months) - 1) / monthlyRate;
        return Math.round(monthlySavings * futureValueFactor);
    }

    /** 전세면 월세 입력값과 무관하게 0으로 고정 */
    private long normalizeMonthlyRent(DealType dealType, Long monthlyRent) {
        if (dealType == DealType.JEONSE) {
            return 0L;
        }
        return monthlyRent != null ? monthlyRent : 0L;
    }

    private void validateRange(int min, int max) {
        if (min > max) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_RANGE);
        }
    }

    private void validateRange(long min, long max) {
        if (min > max) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_RANGE);
        }
    }

    private void validateTargetDate(YearMonth targetDate) {
        if (!targetDate.isAfter(YearMonth.now())) {
            throw new BusinessException(ErrorCode.GOAL_INVALID_DATE);
        }
    }

    private void validateMonthlySavings(long monthlySavings) {
        if (monthlySavings <= 0) {
            throw new BusinessException(ErrorCode.GOAL_MONTHLY_SAVINGS_ZERO);
        }
    }

    private void validateMonthlyRentRequired(DealType dealType, Long monthlyRentMax) {
        if (dealType == DealType.WOLSE && monthlyRentMax == null) {
            throw new BusinessException(ErrorCode.GOAL_MONTHLY_RENT_REQUIRED);
        }
    }
}