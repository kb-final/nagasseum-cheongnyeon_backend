package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PriceModel 배치 조회의 원본 행 프로젝션.
 * (주거유형, 거래유형)마다 배치 쿼리 한 번으로 5개 평수 버킷의 모든 원본 행을 반환하고,
 * 서비스에서 area를 다시 버킷으로 매핑해 조합별로 그룹핑한다.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PriceModelBatchRow {
    private HousingType housingType;
    private DealType dealType;
    private String dealYm;
    private Long deposit;
    private BigDecimal area;
}
