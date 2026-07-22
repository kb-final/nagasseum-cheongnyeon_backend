package com.team.independence.member.dto;

import com.team.independence.member.domain.Member;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 조회 응답 DTO.
 * 도메인 객체를 그대로 노출하지 않고, 화면에 필요한 값만 담아 반환한다.
 */
@Getter
@Builder
public class MemberResponse {

    private final Long id;
    private final String nickname;
    private final String incomeBracket;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .nickname(member.getNickname())
                .incomeBracket(member.getIncomeBracket())
                .build();
    }
}
