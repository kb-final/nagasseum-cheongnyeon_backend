package com.team.independence.asset.controller;

import com.team.independence.asset.dto.AssetLinkRequest;
import com.team.independence.asset.dto.AssetLinkResponse;
import com.team.independence.asset.service.AssetService;
import com.team.independence.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping("/link")
    public ApiResponse<AssetLinkResponse> linkAccount(
            @RequestParam Long memberId,
            @RequestBody AssetLinkRequest request) {
        return ApiResponse.ok(assetService.linkAccount(memberId, request));
    }
}
