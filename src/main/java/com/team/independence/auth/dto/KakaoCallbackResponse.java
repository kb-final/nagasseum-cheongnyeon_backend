package com.team.independence.auth.dto;

public record KakaoCallbackResponse(
        Status status,
        String accessToken,
        String refreshToken,
        Long memberId,
        String kakaoId,
        String kakaoNickname
) {
    public enum Status { LOGIN, SIGNUP_REQUIRED }

    public static KakaoCallbackResponse login(TokenResponse token) {
        return new KakaoCallbackResponse(Status.LOGIN,
                token.accessToken(), token.refreshToken(), token.memberId(),
                null, null);
    }

    public static KakaoCallbackResponse signupRequired(String kakaoId, String kakaoNickname) {
        return new KakaoCallbackResponse(Status.SIGNUP_REQUIRED,
                null, null, null,
                kakaoId, kakaoNickname);
    }
}
