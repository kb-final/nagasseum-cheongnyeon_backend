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

    /** 순자산 = 연동 자산 계좌 합계 + 수동입력 자산 합계 − 연동 대출 잔액 합계 (원) */
    Long getNetAssets(Long memberId);
}
