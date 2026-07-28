package com.team.independence.property.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RentTransaction {
    private Long id;
    private String regionCode;
    private String housingType;
    private String dongName;
    private String jibun;
    private String complexName;
    private BigDecimal area;
    private String dealType;
    private Long deposit;
    private Long monthlyRent;
    private Integer floor;
    private Integer buildYear;
    private String dealYm;
    private String dealDay;
    private String contractType;
    private String contractTerm;
    private String dedupKey;
    private String json;
    private LocalDateTime createdAt;
}
