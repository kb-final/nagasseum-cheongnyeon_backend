package com.team.independence.member.dto;

import com.team.independence.member.domain.IncomeBracket;
import com.team.independence.member.domain.OccupationType;

public record MemberUpdateRequest(
        String nickname,
        IncomeBracket incomeBracket,
        Long monthlyIncome,
        OccupationType occupationType
) {}