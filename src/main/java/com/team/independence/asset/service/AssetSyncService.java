package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetSyncResponse;
import com.team.independence.asset.dto.SyncJobStatusResponse;

public interface AssetSyncService {
    AssetSyncResponse syncAccounts(Long memberId);
    void syncAll();
    SyncJobStatusResponse getSyncStatus(String jobId);
}
