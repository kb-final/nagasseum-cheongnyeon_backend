package com.team.independence.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;

public record KakaoUserInfo(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    public record KakaoAccount(Profile profile) {
        public record Profile(String nickname) {}
    }

    public String getKakaoId() {
        if (id == null) {
            throw new BusinessException(ErrorCode.AUTH_KAKAO_API_ERROR);
        }
        return id.toString();
    }

    public String getNickname() {
        return kakaoAccount != null && kakaoAccount.profile() != null
                ? kakaoAccount.profile().nickname()
                : null;
    }
}