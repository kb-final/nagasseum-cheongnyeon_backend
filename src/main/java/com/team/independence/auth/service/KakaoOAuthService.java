package com.team.independence.auth.service;

import com.team.independence.auth.dto.KakaoCallbackResponse;
import com.team.independence.auth.dto.SignupRequest;
import com.team.independence.auth.dto.TokenResponse;

public interface KakaoOAuthService {

    /**
     * 카카오 인가 코드 처리.
     * 기존 회원이면 JWT를 발급하고, 신규 회원이면 kakaoId·닉네임을 반환한다.
     */
    KakaoCallbackResponse handleCallback(String code);

    /**
     * 회원가입 완료.
     * 추가 정보를 저장하고 JWT를 발급한다.
     */
    TokenResponse signup(SignupRequest request);
}
