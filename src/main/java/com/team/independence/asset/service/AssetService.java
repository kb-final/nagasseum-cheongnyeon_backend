package com.team.independence.asset.service;

import com.team.independence.asset.dto.AssetLinkRequest;
import com.team.independence.asset.dto.AssetLinkResponse;
import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.LinkedOrganizationResponse;
import com.team.independence.asset.dto.UnlinkOrganizationResponse;

import java.util.List;

public interface AssetService {
    AssetLinkResponse linkAccount(Long memberId, AssetLinkRequest request);
    List<LinkedOrganizationResponse> getConnections(Long memberId);
    UnlinkOrganizationResponse unlinkOrganization(Long memberId, String organizationCode);

    /**
     * 예산 계산용 순자산 분해.
     * 예적금은 이자 성장 대상(interestBearingAssets), 그 외(자유입출금/투자 인정액/manual_assets−대출)는
     * flatRecognizedAssets로 원금 그대로 반환한다. 청약(SUBSCRIPTION)은 계산에서 제외.
     */
    AssetNetWorthBreakdown getNetWorthBreakdown(Long memberId);

    /** 자산 연동 여부를 검증한다. 연동된 계좌가 없으면 ASSET_CONNECTION_REQUIRED. */
    void validateConnectedAccountExists(Long memberId);
}
