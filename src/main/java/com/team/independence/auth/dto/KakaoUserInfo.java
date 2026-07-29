package com.team.independence.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfo(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    public record KakaoAccount(Profile profile) {
        public record Profile(String nickname) {}
    }

    public String getKakaoId() {
        return String.valueOf(id);
    }

    public String getNickname() {
        return kakaoAccount != null && kakaoAccount.profile() != null
                ? kakaoAccount.profile().nickname()
                : null;
    }
}