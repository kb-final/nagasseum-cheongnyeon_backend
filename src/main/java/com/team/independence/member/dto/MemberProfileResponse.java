package com.team.independence.member.dto;

import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.Agreement.AgreementType;
import com.team.independence.member.domain.IncomeBracket;
import com.team.independence.member.domain.Member;

import java.util.List;

public record MemberProfileResponse(
        Long id,
        String nickname,
        IncomeBracket incomeBracket,
        boolean notificationAgreed,
        boolean compareDataAgreed
) {
    public static MemberProfileResponse from(Member member, List<Agreement> agreements) {
        return new MemberProfileResponse(
                member.getId(),
                member.getNickname(),
                member.getIncomeBracket(),
                extractAgreed(agreements, AgreementType.NOTIFICATION),
                extractAgreed(agreements, AgreementType.COMPARE_DATA)
        );
    }

    private static boolean extractAgreed(List<Agreement> agreements, AgreementType type) {
        return agreements.stream()
                .filter(a -> a.getAgreementType() == type)
                .map(Agreement::isAgreed)
                .findFirst()
                .orElse(false);
    }
}