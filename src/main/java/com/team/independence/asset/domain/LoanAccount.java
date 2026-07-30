package com.team.independence.asset.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAccount {
    private Long id;
    private Long connectedInstitutionId;
    private String loanName;  // 대출 상품명
    private String accountDisplay;  // 표시용 계좌번호
    private Long loanBalance;  // 대출 잔액
    private String rawResponse;  // CODEF 원본 응답 (JSON)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
