package com.team.independence.auth.controller;

import com.team.independence.auth.dto.RefreshRequest;
import com.team.independence.auth.dto.TokenResponse;
import com.team.independence.auth.service.KakaoOAuthService;
import com.team.independence.common.annotation.LoginMember;
import com.team.independence.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoOAuthService kakaoOAuthService;

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ApiResponse.ok(kakaoOAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@LoginMember Long memberId) {
        kakaoOAuthService.logout(memberId);
        return ApiResponse.ok(null);
    }
}