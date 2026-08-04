package com.team.independence.asset.controller;

import com.team.independence.asset.dto.*;
import com.team.independence.asset.service.AssetAccountListService;
import com.team.independence.asset.service.AssetService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.asset.service.AssetSyncJobStarter;
import com.team.independence.asset.service.AssetSyncService;
import com.team.independence.asset.service.InstitutionService;
import com.team.independence.asset.service.ManualAssetService;
import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final AssetSyncService assetSyncService;
    private final AssetSyncJobStarter assetSyncJobStarter;
    private final AssetSummaryService assetSummaryService;
    private final AssetAccountListService assetAccountListService;
    private final ManualAssetService manualAssetService;
    private final InstitutionService institutionService;

    @PostMapping("/link")
    public ApiResponse<AssetLinkResponse> linkAccount(
            @RequestParam Long memberId,
            @RequestBody AssetLinkRequest request) {
        return ApiResponse.ok(assetService.linkAccount(memberId, request));
    }

    @GetMapping("/organizations")
    public ApiResponse<List<OrganizationResponse>> getOrganizations(
            @LoginMember Long memberId) {
        return ApiResponse.ok(institutionService.getOrganizations(memberId));
    }

    @GetMapping("/connections")
    public ApiResponse<List<LinkedOrganizationResponse>> getConnections(
            @RequestParam Long memberId) {
        return ApiResponse.ok(assetService.getConnections(memberId));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncJobResponse>> startSync(
            @RequestParam Long memberId,
            HttpServletRequest request) {
        SyncJobResponse response = assetSyncJobStarter.start(memberId);
        URI location = UriComponentsBuilder
                .fromHttpUrl(request.getRequestURL().toString())
                .path("/status/{jobId}")
                .buildAndExpand(response.getJobId())
                .toUri();
        return ResponseEntity.accepted()
                .location(location)
                .body(ApiResponse.ok(response));
    }

    @GetMapping("/sync/status/{jobId}")
    public ApiResponse<SyncJobStatusResponse> getSyncStatus(@PathVariable String jobId) {
        return ApiResponse.ok(assetSyncService.getSyncStatus(jobId));
    }

    @GetMapping("/summary/{memberId}")
    public ApiResponse<AssetSummaryResponse> getSummary(@PathVariable Long memberId) {
        return ApiResponse.ok(assetSummaryService.getSummary(memberId));
    }

    @GetMapping("/accounts/{memberId}")
    public ApiResponse<AssetAccountListResponse> getAccountList(@PathVariable Long memberId) {
        return ApiResponse.ok(assetAccountListService.getAccountList(memberId));
    }

    @GetMapping("/manual/{memberId}")
    public ApiResponse<List<ManualAssetResponse>> getManualAssets(@PathVariable Long memberId) {
        return ApiResponse.ok(manualAssetService.getManualAssets(memberId));
    }

    @PostMapping("/manual")
    public ApiResponse<ManualAssetResponse> createManualAsset(
            @RequestParam Long memberId,
            @RequestBody ManualAssetRequest request) {
        return ApiResponse.ok(manualAssetService.createManualAsset(memberId, request));
    }

    @PutMapping("/manual/{id}")
    public ApiResponse<ManualAssetResponse> updateManualAsset(
            @RequestParam Long memberId,
            @PathVariable Long id,
            @RequestBody ManualAssetRequest request) {
        return ApiResponse.ok(manualAssetService.updateManualAsset(memberId, id, request));
    }

    @DeleteMapping("/manual/{id}")
    public ApiResponse<Void> deleteManualAsset(
            @RequestParam Long memberId,
            @PathVariable Long id) {
        manualAssetService.deleteManualAsset(memberId, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/connections/organizations/{organizationCode}")
    public ApiResponse<UnlinkOrganizationResponse> unlinkOrganization(
            @RequestParam Long memberId,
            @PathVariable String organizationCode) {
        return ApiResponse.ok(assetService.unlinkOrganization(memberId, organizationCode));
    }
}
