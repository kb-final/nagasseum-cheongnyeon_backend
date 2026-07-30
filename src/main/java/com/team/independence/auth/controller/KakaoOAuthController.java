package com.team.independence.auth.controller;

import com.team.independence.auth.dto.KakaoCallbackResponse;
import com.team.independence.auth.dto.SignupRequest;
import com.team.independence.auth.dto.TokenResponse;
import com.team.independence.auth.service.KakaoOAuthService;
import com.team.independence.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/oauth/kakao")
@RequiredArgsConstructor
public class KakaoOAuthController {

    private final KakaoOAuthService kakaoOAuthService;

    /**
     * 카카오 OAuth 콜백 처리.
     * <p>
     * 카카오 인가 코드를 받아 기존 회원이면 JWT를 발급하고,
     * 신규 회원이면 kakaoId·닉네임을 반환해 프론트의 추가 정보 입력 단계로 유도한다.
     *
     * @param code 카카오 인가 코드 (redirect_uri로 전달된 1회용 코드)
     * @return status="LOGIN" → JWT 포함 / status="SIGNUP_REQUIRED" → kakaoId·닉네임 포함
     */
    @GetMapping("/callback")
    public ApiResponse<KakaoCallbackResponse> callback(@RequestParam String code) {
        return ApiResponse.ok(kakaoOAuthService.handleCallback(code));
    }

    /**
     * 회원가입 완료.
     * <p>
     * 콜백에서 받은 kakaoId와 프론트에서 입력한 추가 정보로 회원을 생성하고 JWT를 발급한다.
     *
     * @param request kakaoId, nickname, birthDate(YYMMDD)
     * @return 발급된 accessToken·refreshToken·memberId
     */
    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@RequestBody SignupRequest request) {
        return ApiResponse.ok(kakaoOAuthService.signup(request));
    }
}