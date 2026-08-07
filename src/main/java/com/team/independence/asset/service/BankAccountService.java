package com.team.independence.asset.service;

import com.team.independence.asset.dto.account.AssetAccountDetailItem;

import java.util.List;

public interface BankAccountService {
    List<AssetAccountDetailItem> getBankAccounts(Long memberId);
}
