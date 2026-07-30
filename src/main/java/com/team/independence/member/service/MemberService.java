package com.team.independence.member.service;

import com.team.independence.member.domain.IncomeBracket;
import com.team.independence.member.dto.MemberProfileResponse;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 회원 도메인 비즈니스 로직.
 * 인터페이스로 두어 테스트 시 대체 구현을 넣기 쉽게 한다.
 */
public interface MemberService {

    MemberProfileResponse getMember(Long id);

    /** kakaoId로 memberId 조회. 존재하지 않으면 Optional.empty() */
    Optional<Long> findMemberIdByKakaoId(String kakaoId);

    /** 신규 회원 등록 후 memberId 반환. 이미 가입된 kakaoId면 예외 */
    Long createMember(String kakaoId, String nickname, LocalDate birthDate, IncomeBracket incomeBracket);
}
