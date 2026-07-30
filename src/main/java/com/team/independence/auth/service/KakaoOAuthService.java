package com.team.independence.auth.service;

import com.team.independence.auth.dto.KakaoCallbackResponse;
import com.team.independence.auth.dto.SignupRequest;
import com.team.independence.auth.dto.TokenResponse;

public interface KakaoOAuthService {

    KakaoCallbackResponse handleCallback(String code);

    TokenResponse signup(SignupRequest request);

    /** Refresh Token으로 새 Access Token + Refresh Token 발급 (Rotation) */
    TokenResponse refresh(String refreshToken);

    /** 로그아웃 — Redis에서 Refresh Token 삭제 */
    void logout(Long memberId);
}
