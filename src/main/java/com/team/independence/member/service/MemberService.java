package com.team.independence.member.service;

import com.team.independence.member.domain.IncomeBracket;
import com.team.independence.member.domain.OccupationType;
import com.team.independence.member.dto.MemberProfileResponse;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 회원 도메인 비즈니스 로직.
 * 인터페이스로 두어 테스트 시 대체 구현을 넣기 쉽게 한다.
 */
public interface MemberService {

    MemberProfileResponse getMember(Long id);

    /**
     * 월소득만 조회한다. 등록하지 않았으면 null.
     *
     * <p>DSR 한도 계산처럼 소득 한 필드만 필요한 호출부용이다.
     * {@link #getMember(Long)}은 약관 동의 목록까지 함께 읽어 쿼리가 2개 나간다.
     *
     * @throws com.team.independence.common.exception.BusinessException 회원이 없으면 MEMBER_NOT_FOUND
     */
    Long getMonthlyIncome(Long id);

    /** kakaoId로 memberId 조회. 존재하지 않으면 Optional.empty() */
    Optional<Long> findMemberIdByKakaoId(String kakaoId);

    /** 신규 회원 등록 후 memberId 반환. 이미 가입된 kakaoId면 예외 */
    Long createMember(String kakaoId, String nickname, LocalDate birthDate, IncomeBracket incomeBracket);

    /** 닉네임·소득분위·월소득·직업군 수정. null 필드는 변경하지 않음 */
    void updateMember(Long memberId, String nickname, IncomeBracket incomeBracket,
                      Long monthlyIncome, OccupationType occupationType);
}
