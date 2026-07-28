package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetLinkRequest;
import com.team.independence.asset.dto.AssetLinkResponse;

public interface AssetService {
    AssetLinkResponse linkAccount(Long memberId, AssetLinkRequest request);
}
