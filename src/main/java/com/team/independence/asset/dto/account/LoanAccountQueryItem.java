package com.team.independence.asset.dto.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoanAccountQueryItem {
    private Long id;
    private String institutionName;
    private String loanName;
    private String accountDisplay;
    private Long loanBalance;
}
