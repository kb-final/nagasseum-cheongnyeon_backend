package com.team.independence.member.dto;

import com.team.independence.member.domain.Member;

/**
 * 회원 조회 응답 DTO.
 * 도메인 객체를 그대로 노출하지 않고, 화면에 필요한 값만 담아 반환한다.
 */
public record MemberResponse(
        Long id,
        String nickname,
        String incomeBracket
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getNickname(), member.getIncomeBracket());
    }
}