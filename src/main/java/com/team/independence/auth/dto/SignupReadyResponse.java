package com.team.independence.auth.dto;

public record SignupReadyResponse(
        String kakaoId,
        String kakaoNickname
) {}