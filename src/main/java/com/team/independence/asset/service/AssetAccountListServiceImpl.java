package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetAccountDetailItem;
import com.team.independence.asset.dto.AssetAccountListResponse;
import com.team.independence.asset.dto.AssetAccountListResponse.AccountDetail;
import com.team.independence.asset.dto.AssetAccountListResponse.InstitutionGroup;
import com.team.independence.asset.dto.AssetAccountListResponse.LoanDetail;
import com.team.independence.asset.dto.LoanAccountDetailItem;
import com.team.independence.asset.mapper.AssetAccountMapper;
import com.team.independence.asset.mapper.LoanAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetAccountListServiceImpl implements AssetAccountListService {

    private final AssetAccountMapper assetAccountMapper;
    private final LoanAccountMapper loanAccountMapper;

    @Override
    public AssetAccountListResponse getAccountList(Long memberId) {
        List<AssetAccountDetailItem> assetItems = assetAccountMapper.findAllDetailsByMemberId(memberId);
        List<LoanAccountDetailItem> loanItems = loanAccountMapper.findAllDetailsByMemberId(memberId);

        Map<String, List<AccountDetail>> assetByInstitution = new LinkedHashMap<>();
        for (AssetAccountDetailItem item : assetItems) {
            assetByInstitution
                    .computeIfAbsent(item.getInstitutionName(), k -> new ArrayList<>())
                    .add(AccountDetail.builder()
                            .accountType(item.getAccountType())
                            .assetCategory(item.getAssetCategory())
                            .accountDisplay(item.getAccountDisplay())
                            .productName(item.getProductName())
                            .currentValue(item.getCurrentValue())
                            .valuationAmount(item.getValuationAmount())
                            .depositReceived(item.getDepositReceived())
                            .valuationPl(item.getValuationPl())
                            .purchaseAmount(item.getPurchaseAmount())
                            .earningsRate(item.getEarningsRate())
                            .startDate(item.getStartDate())
                            .maturityDate(item.getMaturityDate())
                            .build());
        }

        Map<String, List<LoanDetail>> loanByInstitution = new LinkedHashMap<>();
        for (LoanAccountDetailItem item : loanItems) {
            loanByInstitution
                    .computeIfAbsent(item.getInstitutionName(), k -> new ArrayList<>())
                    .add(LoanDetail.builder()
                            .loanName(item.getLoanName())
                            .accountDisplay(item.getAccountDisplay())
                            .loanBalance(item.getLoanBalance())
                            .startDate(item.getStartDate())
                            .endDate(item.getEndDate())
                            .build());
        }

        // 자산 / 대출 기관명 합집합으로 그룹 생성
        Map<String, InstitutionGroup> groupMap = new LinkedHashMap<>();
        assetByInstitution.forEach((name, accounts) ->
                groupMap.put(name, InstitutionGroup.builder()
                        .institutionName(name)
                        .assetAccounts(accounts)
                        .loanAccounts(new ArrayList<>())
                        .build()));

        loanByInstitution.forEach((name, loans) -> {
            if (groupMap.containsKey(name)) {
                groupMap.get(name).getLoanAccounts().addAll(loans);
            } else {
                groupMap.put(name, InstitutionGroup.builder()
                        .institutionName(name)
                        .assetAccounts(new ArrayList<>())
                        .loanAccounts(loans)
                        .build());
            }
        });

        return AssetAccountListResponse.builder()
                .institutions(new ArrayList<>(groupMap.values()))
                .build();
    }
}
