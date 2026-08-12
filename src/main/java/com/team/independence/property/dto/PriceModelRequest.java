package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 가격 모델(μ, σ) 추정 조건
 *
 * RentMedianRequest와 달리 보증금, 월세 필터가 없다.
 * 시장 전체 거래 흐름을 시계열로 분석하므로 사용자 예산 범위로 좁히지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PriceModelRequest {

    @NotBlank(message = "지역 코드는 필수입니다.")
    private String regionCode;

    @NotNull(message = "주거 형태는 필수입니다.")
    private HousingType housingType;

    @NotNull(message = "거래 유형은 필수입니다.")
    private DealType dealType;

    @NotNull(message = "최소 평수는 필수입니다.")
    @Positive(message = "최소 평수는 0보다 커야 합니다.")
    private Integer areaMin;

    @NotNull(message = "최대 평수는 필수입니다.")
    @Positive(message = "최대 평수는 0보다 커야 합니다.")
    private Integer areaMax;

    @AssertTrue(message = "최소 평수는 최대 평수보다 클 수 없습니다.")
    public boolean isAreaRangeValid() {
        return areaMin == null || areaMax == null || areaMin <= areaMax;
    }
}
