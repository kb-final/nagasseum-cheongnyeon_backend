package com.team.independence.asset.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AssetSummaryResponse {
    private Long memberId;
    private Long totalAssets;
    private Long loanBalance;
    private Long netAssets;
    private Long monthlySavings;
    private LocalDateTime syncedAt;
    private AssetBreakdown assetBreakdown;
    private List<LoanItem> loans;

    @Getter
    @Builder
    public static class AssetBreakdown {
        private AssetGroup cashAssets;
        private AssetGroup investmentAssets;
    }

    @Getter
    @Builder
    public static class AssetGroup {
        private Long total;
        private List<AccountItem> accounts;
    }

    @Getter
    @Builder
    public static class AccountItem {
        private String institutionName;
        private String accountType;
        private String productName;
        private String accountDisplay;
        private Long balance;
    }

    @Getter
    @Builder
    public static class LoanItem {
        private String institutionName;
        private String loanName;
        private String accountDisplay;
        private Long loanBalance;
    }
}
