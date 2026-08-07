package com.team.independence.asset.service;

import com.team.independence.asset.domain.summary.AssetSummary;
import com.team.independence.asset.dto.account.AssetAccountQueryItem;
import com.team.independence.asset.dto.account.LoanAccountQueryItem;
import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.summary.AssetSummaryResponse;
import com.team.independence.asset.dto.summary.AssetSummaryResponse.*;
import com.team.independence.asset.mapper.AssetAccountMapper;
import com.team.independence.asset.mapper.AssetSummaryMapper;
import com.team.independence.asset.mapper.LoanAccountMapper;
import com.team.independence.asset.mapper.ManualAssetMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetSummaryServiceImpl implements AssetSummaryService {

    private static final double INVESTMENT_RECOGNITION_RATE = 0.7;

    private final AssetSummaryMapper assetSummaryMapper;
    private final AssetAccountMapper assetAccountMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final ManualAssetMapper manualAssetMapper;

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
            long balance = computeBalance(account);
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

    @Override
    @Transactional
    public void updateMonthlySavings(Long memberId, Long monthlySavings) {
        assetSummaryMapper.upsertMonthlySavings(memberId, monthlySavings);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetNetWorthBreakdown getNetWorthBreakdown(Long memberId) {
        long interestBearingAssets = assetAccountMapper.sumCurrentValueByMemberIdAndCategories(
                memberId, List.of("DEPOSIT_SAVINGS"));
        long investmentRecognized = investmentRecognizedAmount(memberId);
        long cashAndEtcAssets = assetAccountMapper.sumCurrentValueByMemberIdAndCategories(
                memberId, List.of("CASH", "ETC"));
        long manualAssets = manualAssetMapper.sumAmountByMemberId(memberId);
        long loanBalance = loanAccountMapper.sumLoanBalanceByMemberId(memberId);

        long flatRecognizedAssets = investmentRecognized + cashAndEtcAssets + manualAssets - loanBalance;

        return AssetNetWorthBreakdown.builder()
                .interestBearingAssets(interestBearingAssets)
                .flatRecognizedAssets(flatRecognizedAssets)
                .build();
    }

    private long investmentRecognizedAmount(Long memberId) {
        long total = 0L;
        for (AssetAccountQueryItem account : assetAccountMapper.findWithInstitutionByMemberId(memberId)) {
            if (!"INVESTMENT".equals(account.getAssetCategory())) {
                continue;
            }
            String type = account.getAccountType();
            if (("STOCK".equals(type) || "FUND".equals(type)) && account.getValuationAmount() != null) {
                long deposit = account.getDepositReceived() != null ? account.getDepositReceived() : 0L;
                total += Math.round(account.getValuationAmount() * INVESTMENT_RECOGNITION_RATE) + deposit;
            } else {
                total += account.getCurrentValue() != null ? account.getCurrentValue() : 0L;
            }
        }
        return total;
    }

    private long computeBalance(AssetAccountQueryItem account) {
        String type = account.getAccountType();
        if (("STOCK".equals(type) || "FUND".equals(type)) && account.getValuationAmount() != null) {
            long valuation = account.getValuationAmount();
            long deposit = account.getDepositReceived() != null ? account.getDepositReceived() : 0L;
            return Math.round(valuation * 0.7) + deposit;
        }
        return account.getCurrentValue() != null ? account.getCurrentValue() : 0L;
    }

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
