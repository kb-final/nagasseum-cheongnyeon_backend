package com.team.independence.asset.controller;

import com.team.independence.asset.dto.account.AssetAccountDetailItem;
import com.team.independence.asset.dto.account.AssetAccountListResponse;
import com.team.independence.asset.dto.account.CardAccountResponse;
import com.team.independence.asset.dto.account.LoanAccountDetailItem;
import com.team.independence.asset.dto.connection.AssetLinkRequest;
import com.team.independence.asset.dto.connection.AssetLinkResponse;
import com.team.independence.asset.dto.connection.LinkedOrganizationResponse;
import com.team.independence.asset.dto.connection.OrganizationResponse;
import com.team.independence.asset.dto.connection.UnlinkOrganizationResponse;
import com.team.independence.asset.dto.manual.ManualAssetRequest;
import com.team.independence.asset.dto.manual.ManualAssetResponse;
import com.team.independence.asset.dto.summary.AssetSummaryResponse;
import com.team.independence.asset.dto.sync.SyncJobResponse;
import com.team.independence.asset.dto.sync.SyncJobStatusResponse;
import com.team.independence.asset.service.AssetConnectionService;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.asset.service.AssetSyncJobStarter;
import com.team.independence.asset.service.AssetSyncService;
import com.team.independence.asset.service.BankAccountService;
import com.team.independence.asset.service.CardAccountService;
import com.team.independence.asset.service.InstitutionService;
import com.team.independence.asset.service.LoanAccountService;
import com.team.independence.asset.service.ManualAssetService;
import com.team.independence.asset.service.StockAccountService;
import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetConnectionService assetConnectionService;
    private final AssetSyncService assetSyncService;
    private final AssetSyncJobStarter assetSyncJobStarter;
    private final AssetSummaryService assetSummaryService;
    private final BankAccountService bankAccountService;
    private final StockAccountService stockAccountService;
    private final LoanAccountService loanAccountService;
    private final CardAccountService cardAccountService;
    private final ManualAssetService manualAssetService;
    private final InstitutionService institutionService;

    @PostMapping("/link")
    public ApiResponse<AssetLinkResponse> linkAccount(
            @LoginMember Long memberId,
            @RequestBody AssetLinkRequest request) {
        return ApiResponse.ok(assetConnectionService.linkAccount(memberId, request));
    }

    @GetMapping("/organizations")
    public ApiResponse<List<OrganizationResponse>> getOrganizations(
            @LoginMember Long memberId) {
        return ApiResponse.ok(institutionService.getOrganizations(memberId));
    }

    @GetMapping("/connections")
    public ApiResponse<List<LinkedOrganizationResponse>> getConnections(
            @LoginMember Long memberId) {
        return ApiResponse.ok(assetConnectionService.getConnections(memberId));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncJobResponse>> startSync(
            @LoginMember Long memberId,
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

    @GetMapping("/summary")
    public ApiResponse<AssetSummaryResponse> getSummary(@LoginMember Long memberId) {
        return ApiResponse.ok(assetSummaryService.getSummary(memberId));
    }

    @GetMapping("/accounts")
    public ApiResponse<AssetAccountListResponse> getAccountList(@LoginMember Long memberId) {
        List<AssetAccountDetailItem> bankItems = bankAccountService.getBankAccounts(memberId);
        List<AssetAccountDetailItem> stockItems = stockAccountService.getStockAccounts(memberId);
        List<LoanAccountDetailItem> loanItems = loanAccountService.getLoanAccounts(memberId);

        Map<String, List<AssetAccountListResponse.AccountDetail>> assetByInstitution = new LinkedHashMap<>();
        for (AssetAccountDetailItem item : bankItems) {
            assetByInstitution.computeIfAbsent(item.getInstitutionName(), k -> new ArrayList<>())
                    .add(toAccountDetail(item));
        }
        for (AssetAccountDetailItem item : stockItems) {
            assetByInstitution.computeIfAbsent(item.getInstitutionName(), k -> new ArrayList<>())
                    .add(toAccountDetail(item));
        }
        Map<String, List<AssetAccountListResponse.LoanDetail>> loanByInstitution = new LinkedHashMap<>();
        for (LoanAccountDetailItem item : loanItems) {
            loanByInstitution.computeIfAbsent(item.getInstitutionName(), k -> new ArrayList<>())
                    .add(toLoanDetail(item));
        }
        Map<String, AssetAccountListResponse.InstitutionGroup> groupMap = new LinkedHashMap<>();
        assetByInstitution.forEach((name, accounts) ->
                groupMap.put(name, AssetAccountListResponse.InstitutionGroup.builder()
                        .institutionName(name)
                        .assetAccounts(accounts)
                        .loanAccounts(new ArrayList<>())
                        .build()));
        loanByInstitution.forEach((name, loans) -> {
            if (groupMap.containsKey(name)) {
                groupMap.get(name).getLoanAccounts().addAll(loans);
            } else {
                groupMap.put(name, AssetAccountListResponse.InstitutionGroup.builder()
                        .institutionName(name)
                        .assetAccounts(new ArrayList<>())
                        .loanAccounts(loans)
                        .build());
            }
        });
        return ApiResponse.ok(AssetAccountListResponse.builder()
                .institutions(new ArrayList<>(groupMap.values()))
                .build());
    }

    private AssetAccountListResponse.AccountDetail toAccountDetail(AssetAccountDetailItem item) {
        return AssetAccountListResponse.AccountDetail.builder()
                .accountType(item.getAccountType())
                .assetCategory(item.getAssetCategory())
                .accountDisplay(item.getAccountDisplay())
                .productName(item.getProductName())
                .currentValue(item.getCurrentValue())
                .valuationAmount(item.getValuationAmount())
                .depositReceived(item.getDepositReceived())
                .valuationPl(item.getValuationPl())
                .purchaseAmount(item.getPurchaseAmount())
                .earningsRate(item.getEarningsRate())
                .startDate(item.getStartDate())
                .maturityDate(item.getMaturityDate())
                .build();
    }

    private AssetAccountListResponse.LoanDetail toLoanDetail(LoanAccountDetailItem item) {
        return AssetAccountListResponse.LoanDetail.builder()
                .loanName(item.getLoanName())
                .accountDisplay(item.getAccountDisplay())
                .loanBalance(item.getLoanBalance())
                .startDate(item.getStartDate())
                .endDate(item.getEndDate())
                .build();
    }

    @GetMapping("/cards")
    public ApiResponse<List<CardAccountResponse>> getCardList(@LoginMember Long memberId) {
        return ApiResponse.ok(cardAccountService.getCardList(memberId));
    }

    @GetMapping("/manual")
    public ApiResponse<List<ManualAssetResponse>> getManualAssets(@LoginMember Long memberId) {
        return ApiResponse.ok(manualAssetService.getManualAssets(memberId));
    }

    @PostMapping("/manual")
    public ApiResponse<ManualAssetResponse> createManualAsset(
            @LoginMember Long memberId,
            @RequestBody ManualAssetRequest request) {
        return ApiResponse.ok(manualAssetService.createManualAsset(memberId, request));
    }

    @PutMapping("/manual/{id}")
    public ApiResponse<ManualAssetResponse> updateManualAsset(
            @LoginMember Long memberId,
            @PathVariable Long id,
            @RequestBody ManualAssetRequest request) {
        return ApiResponse.ok(manualAssetService.updateManualAsset(memberId, id, request));
    }

    @DeleteMapping("/manual/{id}")
    public ApiResponse<Void> deleteManualAsset(
            @LoginMember Long memberId,
            @PathVariable Long id) {
        manualAssetService.deleteManualAsset(memberId, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/connections/organizations/{organizationCode}")
    public ApiResponse<UnlinkOrganizationResponse> unlinkOrganization(
            @LoginMember Long memberId,
            @PathVariable String organizationCode) {
        return ApiResponse.ok(assetConnectionService.unlinkOrganization(memberId, organizationCode));
    }
}
