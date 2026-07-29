package com.team.independence.auth.dto;

public record SignupRequest(
        String kakaoId,
        String nickname,
        String birthDate  // YYMMDD (6자리)
) {}
