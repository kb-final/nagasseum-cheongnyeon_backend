package com.team.independence.asset.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AssetAccountListResponse {

    private List<InstitutionGroup> institutions;

    @Getter
    @Builder
    public static class InstitutionGroup {
        private String institutionName;
        private List<AccountDetail> assetAccounts;
        private List<LoanDetail> loanAccounts;
    }

    @Getter
    @Builder
    public static class AccountDetail {
        private String accountType;
        private String assetCategory;
        private String accountDisplay;
        private String productName;
        private Long currentValue;
        private Long valuationAmount;
        private Long depositReceived;
        private Long valuationPl;
        private Long purchaseAmount;
        private BigDecimal earningsRate;
        private LocalDate startDate;
        private LocalDate maturityDate;
    }

    @Getter
    @Builder
    public static class LoanDetail {
        private String loanName;
        private String accountDisplay;
        private Long loanBalance;
        private LocalDate startDate;
        private LocalDate endDate;
    }
}
