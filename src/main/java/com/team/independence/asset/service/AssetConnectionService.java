package com.team.independence.asset.service;

import com.team.independence.asset.dto.connection.AssetLinkRequest;
import com.team.independence.asset.dto.connection.AssetLinkResponse;
import com.team.independence.asset.dto.connection.LinkedOrganizationResponse;
import com.team.independence.asset.dto.connection.UnlinkOrganizationResponse;

import java.util.List;

public interface AssetConnectionService {
    AssetLinkResponse linkAccount(Long memberId, AssetLinkRequest request);
    List<LinkedOrganizationResponse> getConnections(Long memberId);
    UnlinkOrganizationResponse unlinkOrganization(Long memberId, String organizationCode);
    void validateConnectedAccountExists(Long memberId);
}
