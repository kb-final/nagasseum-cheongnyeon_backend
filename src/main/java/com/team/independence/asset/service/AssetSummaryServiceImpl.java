package com.team.independence.asset.service;

import com.team.independence.asset.domain.AssetSummary;
import com.team.independence.asset.dto.AssetAccountQueryItem;
import com.team.independence.asset.dto.AssetSummaryResponse;
import com.team.independence.asset.dto.AssetSummaryResponse.*;
import com.team.independence.asset.dto.LoanAccountQueryItem;
import com.team.independence.asset.mapper.AssetAccountMapper;
import com.team.independence.asset.mapper.AssetSummaryMapper;
import com.team.independence.asset.mapper.LoanAccountMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetSummaryServiceImpl implements AssetSummaryService {

    private final AssetSummaryMapper assetSummaryMapper;
    private final AssetAccountMapper assetAccountMapper;
    private final LoanAccountMapper loanAccountMapper;

    @Override
    public AssetSummaryResponse getSummary(Long memberId) {
        AssetSummary summary = assetSummaryMapper.findByMemberId(memberId);
        if (summary == null) {
            throw new BusinessException(ErrorCode.ASSET_SUMMARY_NOT_FOUND);
        }

        List<AssetAccountQueryItem> accounts = assetAccountMapper.findWithInstitutionByMemberId(memberId);
        List<LoanAccountQueryItem> loans = loanAccountMapper.findWithInstitutionByMemberId(memberId);

        List<AccountItem> cashList = new ArrayList<>();
        List<AccountItem> investmentList = new ArrayList<>();
        long cashTotal = 0L;
        long investmentTotal = 0L;

        for (AssetAccountQueryItem account : accounts) {
            long balance = account.getCurrentValue() != null ? account.getCurrentValue() : 0L;
            AccountItem item = AccountItem.builder()
                    .institutionName(account.getInstitutionName())
                    .accountType(toResponseAccountType(account.getAccountType()))
                    .productName(account.getProductName())
                    .accountDisplay(account.getAccountDisplay())
                    .balance(balance)
                    .build();

            String category = account.getAssetCategory();
            if ("INVESTMENT".equals(category)) {
                investmentList.add(item);
                investmentTotal += balance;
            } else {
                // CASH, DEPOSIT_SAVINGS, SUBSCRIPTION 모두 현금성 자산
                cashList.add(item);
                cashTotal += balance;
            }
        }

        List<LoanItem> loanItems = new ArrayList<>();
        for (LoanAccountQueryItem loan : loans) {
            loanItems.add(LoanItem.builder()
                    .institutionName(loan.getInstitutionName())
                    .loanName(loan.getLoanName())
                    .accountDisplay(loan.getAccountDisplay())
                    .loanBalance(loan.getLoanBalance())
                    .build());
        }

        long totalAssets = summary.getTotalAssets();
        long loanBalance = summary.getLoanBalance();

        return AssetSummaryResponse.builder()
                .memberId(memberId)
                .totalAssets(totalAssets)
                .loanBalance(loanBalance)
                .netAssets(totalAssets - loanBalance)
                .monthlySavings(summary.getMonthlySavings())
                .syncedAt(summary.getSyncedAt())
                .assetBreakdown(AssetBreakdown.builder()
                        .cashAssets(AssetGroup.builder()
                                .total(cashTotal)
                                .accounts(cashList)
                                .build())
                        .investmentAssets(AssetGroup.builder()
                                .total(investmentTotal)
                                .accounts(investmentList)
                                .build())
                        .build())
                .loans(loanItems)
                .build();
    }

    /**
     * 내부 account_type → API 응답 accountType
     * DEMAND(입출금), DEPOSIT(정기예금) → DEPOSIT
     * SAVINGS(적금), SUBSCRIPTION(청약) → SAVINGS
     */
    private String toResponseAccountType(String accountType) {
        switch (accountType) {
            case "DEMAND":
            case "DEPOSIT": return "DEPOSIT";
            case "SAVINGS":
            case "SUBSCRIPTION": return "SAVINGS";
            case "FUND": return "FUND";
            case "STOCK": return "STOCK";
            default: return accountType;
        }
    }
}
