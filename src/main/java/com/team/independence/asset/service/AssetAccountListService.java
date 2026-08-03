package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetAccountListResponse;

public interface AssetAccountListService {
    AssetAccountListResponse getAccountList(Long memberId);
}
