package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 실거래 매물 중앙값 조회 조건.
 *
 * <p>쿼리 파라미터를 @ModelAttribute로 받으므로 기본 생성자와 setter가 필요하다.
 * (응답 DTO의 @Builder 패턴과 다른 이유)
 */
@Getter
@Setter
@NoArgsConstructor
public class RentMedianRequest {

    /** 지역 코드(법정동코드 앞 5자리) */
    @NotBlank(message = "지역 코드는 필수입니다.")
    private String regionCode;

    /** 주거 형태 */
    @NotNull(message = "주거 형태는 필수입니다.")
    private HousingType housingType;

    /** 거래 유형 */
    @NotNull(message = "거래 유형은 필수입니다.")
    private DealType dealType;

    /** 희망 최소 평수(평) */
    @NotNull(message = "최소 평수는 필수입니다.")
    @Positive(message = "최소 평수는 0보다 커야 합니다.")
    private Integer areaMin;

    /** 희망 최대 평수(평) */
    @NotNull(message = "최대 평수는 필수입니다.")
    @Positive(message = "최대 평수는 0보다 커야 합니다.")
    private Integer areaMax;

    /** 희망 최소 보증금(원) */
    @NotNull(message = "최소 보증금은 필수입니다.")
    @PositiveOrZero(message = "최소 보증금은 0 이상이어야 합니다.")
    private Long depositMin;

    /** 희망 최대 보증금(원) */
    @NotNull(message = "최대 보증금은 필수입니다.")
    @PositiveOrZero(message = "최대 보증금은 0 이상이어야 합니다.")
    private Long depositMax;

    /** 희망 최소 월세(원). dealType=WOLSE 일 때만 사용 */
    @PositiveOrZero(message = "최소 월세는 0 이상이어야 합니다.")
    private Long monthlyRentMin;

    /** 희망 최대 월세(원). dealType=WOLSE 일 때만 사용 */
    @PositiveOrZero(message = "최대 월세는 0 이상이어야 합니다.")
    private Long monthlyRentMax;

    /**
     * min/max 교차 검증은 개별 필드 제약으로 표현할 수 없어 @AssertTrue로 처리한다.
     * null 검사는 @NotNull이 이미 하므로 여기서는 통과시켜 에러 메시지가 중복되지 않게 한다.
     */
    @AssertTrue(message = "최소 평수는 최대 평수보다 클 수 없습니다.")
    public boolean isAreaRangeValid() {
        return areaMin == null || areaMax == null || areaMin <= areaMax;
    }

    @AssertTrue(message = "최소 보증금은 최대 보증금보다 클 수 없습니다.")
    public boolean isDepositRangeValid() {
        return depositMin == null || depositMax == null || depositMin <= depositMax;
    }

    @AssertTrue(message = "최소 월세는 최대 월세보다 클 수 없습니다.")
    public boolean isMonthlyRentRangeValid() {
        return monthlyRentMin == null || monthlyRentMax == null || monthlyRentMin <= monthlyRentMax;
    }

    /** 월세 조건은 월세 거래에만 의미가 있다. 전세 조회 시 들어온 값은 무시한다. */
    public boolean usesMonthlyRentFilter() {
        return dealType == DealType.WOLSE;
    }
}
