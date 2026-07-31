package com.team.independence.member.dto;

import com.team.independence.member.domain.IncomeBracket;

public record MemberUpdateRequest(
        String nickname,
        IncomeBracket incomeBracket
) {}