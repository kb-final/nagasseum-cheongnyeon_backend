package com.team.independence.asset.service;

import com.team.independence.asset.dto.account.AssetAccountDetailItem;

import java.util.List;

public interface StockAccountService {
    List<AssetAccountDetailItem> getStockAccounts(Long memberId);
}
