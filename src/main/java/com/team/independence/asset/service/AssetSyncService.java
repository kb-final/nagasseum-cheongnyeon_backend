package com.team.independence.asset.service;

import com.team.independence.asset.dto.sync.AssetSyncResponse;
import com.team.independence.asset.dto.sync.SyncJobStatusResponse;

public interface AssetSyncService {
    AssetSyncResponse syncAccounts(Long memberId);
    void syncAll();
    SyncJobStatusResponse getSyncStatus(String jobId);
}
