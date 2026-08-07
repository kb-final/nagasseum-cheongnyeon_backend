package com.team.independence.asset.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardAccount {
    private Long id;
    private Long connectedInstitutionId;
    private String cardNo;
    private String isSleep;  // Y / N
    private String cardName;
    private String cardType;  // 신용 / 체크
    private String isTraffic;  // Y / N
    private String imageLink;
    private LocalDate issueDate;
    private String validPeriod;  // YYYYMM
    private String state;
    private String rawResponse;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
