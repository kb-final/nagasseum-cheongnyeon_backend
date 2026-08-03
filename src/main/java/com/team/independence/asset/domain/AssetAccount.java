package com.team.independence.asset.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetAccount {
    private Long id;
    private Long connectedInstitutionId;
    private String accountType;  // DEMAND / DEPOSIT / SAVINGS / SUBSCRIPTION / FUND / STOCK
    private String assetCategory;  // CASH / DEPOSIT_SAVINGS / INVESTMENT / SUBSCRIPTION / ETC
    private String accountDisplay;  // 표시용 계좌번호
    private String productName;  // 계좌명/상품명
    private Long currentValue;  // 현재가치 (NULL = 기준가 미공시)
    private Long valuationAmount;  // 증권 평가금액
    private Long depositReceived;  // 예수금
    private Long valuationPl;  // 평가손익 (증권)
    private Long purchaseAmount;  // 매입금액 (증권)
    private BigDecimal earningsRate;  // 수익률 (%)
    private LocalDate startDate;  // 계좌 개설일 (은행)
    private LocalDate maturityDate;  // 만기일 (은행)
    private String rawResponse;  // CODEF 원본 응답 (JSON)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
