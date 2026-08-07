package com.team.independence.asset.dto.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class LoanAccountDetailItem {
    private String institutionName;
    private String loanName;
    private String accountDisplay;
    private Long loanBalance;
    private LocalDate startDate;
    private LocalDate endDate;
}
