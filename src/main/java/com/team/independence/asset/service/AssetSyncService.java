package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetSyncResponse;

public interface AssetSyncService {
    AssetSyncResponse syncAccounts(Long memberId);
    void syncAll();
}
