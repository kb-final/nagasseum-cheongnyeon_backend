package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 목표 추천 요청 조건. 모든 필드가 선택값이며, 비어 있는 조건은 각 추천 알고리즘이 알아서 채운다.
 *
 * <p>조건을 얼마나 참고할지는 알고리즘마다 다르다. 사용자 조건을 그대로 쓰는 알고리즘도 있고,
 * 일부만 참고하거나 아예 무시하는 알고리즘도 있다.
 *
 * <p>min/max 교차 검증은 개별 필드 제약으로 표현할 수 없어 {@code @AssertTrue}로 처리한다.
 * 값이 null이면 "조건 없음"이므로 통과시킨다.
 */
@Getter
@Setter
@NoArgsConstructor
public class GoalRecommendationRequest {

    /**
     * 법정동코드 앞 5자리(시군구) 또는 시도 2자리. 유일한 필수 조건이다.
     *
     * <p>추천이 사용자가 지정한 시도를 벗어나지 않기 때문에 탐색 범위를 여는 기준점이 되고,
     * 지역 없이는 어떤 알고리즘도 실거래를 조회할 수 없어 필수로 둔다.
     * 시도 2자리면 시군구는 알고리즘이 정하고, 5자리면 그 시군구로 고정된다.
     */
    @NotBlank(message = "지역 코드는 필수입니다.")
    @Pattern(regexp = "\\d{2}|\\d{5}", message = "지역 코드는 시도 2자리 또는 시군구 5자리여야 합니다.")
    private String regionCode;

    /**
     * 월 저축액(원). 필수.
     *
     * <p>예산 계산의 핵심 입력이라 사용자가 직접 넘긴다. 서버가 보관한 asset_summary 캐시가 아니라
     * 이 값을 그대로 쓴다 — 추천 화면에서 사용자가 저축액을 조정해 시나리오를 보기 때문이다.
     */
    @NotNull(message = "월 저축액은 필수입니다.")
    @PositiveOrZero(message = "월 저축액은 0 이상이어야 합니다.")
    private Long monthlySavings;

    /** 선택. null이면 전 주거유형 탐색 */
    private HousingType propertyType;

    /** 선택. null이면 전 거래유형 탐색 */
    private DealType tradeType;

    /** 선택. 단위: 평. null이면 알고리즘이 판단 */
    @Positive(message = "최소 평수는 0보다 커야 합니다.")
    private Integer sizeMin;

    /** 선택. 단위: 평. null이면 알고리즘이 판단 */
    @Positive(message = "최대 평수는 0보다 커야 합니다.")
    private Integer sizeMax;

    /** 선택. 희망 보증금 하한 (원) */
    @PositiveOrZero(message = "최소 보증금은 0 이상이어야 합니다.")
    private Long depositMin;

    /** 선택. 희망 보증금 상한 (원) */
    @PositiveOrZero(message = "최대 보증금은 0 이상이어야 합니다.")
    private Long depositMax;

    /** 희망 월세 하한 (원). tradeType이 WOLSE일 때만 의미 있다 */
    @PositiveOrZero(message = "최소 월세는 0 이상이어야 합니다.")
    private Long monthlyRentMin;

    /** 희망 월세 상한 (원). tradeType이 WOLSE일 때만 의미 있다 */
    @PositiveOrZero(message = "최대 월세는 0 이상이어야 합니다.")
    private Long monthlyRentMax;

    /** 선택. 목표 시점(yyyy-MM). null이면 알고리즘이 판단 */
    @DateTimeFormat(pattern = "yyyy-MM")
    private YearMonth targetDate;

    @AssertTrue(message = "최소 평수는 최대 평수보다 클 수 없습니다.")
    public boolean isSizeRangeValid() {
        return sizeMin == null || sizeMax == null || sizeMin <= sizeMax;
    }

    @AssertTrue(message = "최소 보증금은 최대 보증금보다 클 수 없습니다.")
    public boolean isDepositRangeValid() {
        return depositMin == null || depositMax == null || depositMin <= depositMax;
    }

    @AssertTrue(message = "최소 월세는 최대 월세보다 클 수 없습니다.")
    public boolean isMonthlyRentRangeValid() {
        return monthlyRentMin == null || monthlyRentMax == null || monthlyRentMin <= monthlyRentMax;
    }

    /** 월세를 지정했다면 월세 상한은 있어야 범위 탐색이 가능하다. */
    @AssertTrue(message = "거래 유형이 월세면 월세 범위는 필수입니다.")
    public boolean isMonthlyRentRequiredForWolse() {
        return tradeType != DealType.WOLSE || monthlyRentMax != null;
    }

    /** 목표 시점을 지정했다면 현재 이후여야 한다. */
    @AssertTrue(message = "목표 시점은 현재 이후여야 합니다.")
    public boolean isTargetDateValid() {
        return targetDate == null || !targetDate.isBefore(YearMonth.now());
    }
}
