package com.team.independence.asset.controller;

import com.team.independence.asset.dto.*;
import com.team.independence.asset.service.AssetService;
import com.team.independence.asset.service.InstitutionService;
import com.team.independence.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final InstitutionService institutionService;

    @PostMapping("/link")
    public ApiResponse<AssetLinkResponse> linkAccount(
            @RequestParam Long memberId,
            @RequestBody AssetLinkRequest request) {
        return ApiResponse.ok(assetService.linkAccount(memberId, request));
    }

    @GetMapping("/organizations")
    public ApiResponse<List<OrganizationResponse>> getOrganizations() {
        return ApiResponse.ok(institutionService.getOrganizations());
    }

    @GetMapping("/connections")
    public ApiResponse<List<LinkedOrganizationResponse>> getConnections(
            @RequestParam Long memberId) {
        return ApiResponse.ok(assetService.getConnections(memberId));
    }

    @DeleteMapping("/connections/organizations/{organizationCode}")
    public ApiResponse<UnlinkOrganizationResponse> unlinkOrganization(
            @RequestParam Long memberId,
            @PathVariable String organizationCode) {
        return ApiResponse.ok(assetService.unlinkOrganization(memberId, organizationCode));
    }
}
