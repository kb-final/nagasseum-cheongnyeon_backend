package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetLinkRequest;
import com.team.independence.asset.dto.AssetLinkResponse;
import com.team.independence.asset.dto.LinkedOrganizationResponse;
import com.team.independence.asset.dto.UnlinkOrganizationResponse;

import java.util.List;

public interface AssetService {
    AssetLinkResponse linkAccount(Long memberId, AssetLinkRequest request);
    List<LinkedOrganizationResponse> getConnections(Long memberId);
    UnlinkOrganizationResponse unlinkOrganization(Long memberId, String organizationCode);
}
