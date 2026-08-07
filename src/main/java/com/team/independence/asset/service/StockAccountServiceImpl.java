package com.team.independence.asset.service;

import com.team.independence.asset.dto.account.AssetAccountDetailItem;
import com.team.independence.asset.mapper.AssetAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockAccountServiceImpl implements StockAccountService {

    private final AssetAccountMapper assetAccountMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AssetAccountDetailItem> getStockAccounts(Long memberId) {
        return assetAccountMapper.findStockAccountDetailsByMemberId(memberId);
    }
}
