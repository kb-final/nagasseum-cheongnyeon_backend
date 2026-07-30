package com.team.independence.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        Long memberId
) {}