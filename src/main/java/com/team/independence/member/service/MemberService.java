package com.team.independence.member.service;

import com.team.independence.member.dto.MemberResponse;

/**
 * 회원 도메인 비즈니스 로직.
 * 인터페이스로 두어 테스트 시 대체 구현을 넣기 쉽게 한다.
 */
public interface MemberService {

    MemberResponse getMember(Long id);
}
