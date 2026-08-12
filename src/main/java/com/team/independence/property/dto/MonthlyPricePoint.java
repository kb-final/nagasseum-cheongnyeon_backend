package com.team.independence.property.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 월별 평단가 시계열 산출용 mapper 프로젝션 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyPricePoint {
    private String dealYm;
    private Long deposit;
    private BigDecimal area;
}
