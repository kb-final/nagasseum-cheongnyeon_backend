package com.team.independence.asset.service;

import com.team.independence.asset.dto.ManualAssetRequest;
import com.team.independence.asset.dto.ManualAssetResponse;

import java.util.List;

public interface ManualAssetService {
    List<ManualAssetResponse> getManualAssets(Long memberId);
    ManualAssetResponse createManualAsset(Long memberId, ManualAssetRequest request);
    ManualAssetResponse updateManualAsset(Long memberId, Long id, ManualAssetRequest request);
    void deleteManualAsset(Long memberId, Long id);
}
