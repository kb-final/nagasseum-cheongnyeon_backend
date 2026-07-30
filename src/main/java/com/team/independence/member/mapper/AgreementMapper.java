package com.team.independence.member.mapper;

import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.Agreement.AgreementType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AgreementMapper {

    /** 회원의 전체 약관 동의 이력 조회 */
    List<Agreement> findByMemberId(Long memberId);

    /** 특정 약관 유형 조회 */
    Optional<Agreement> findByMemberIdAndType(@Param("memberId") Long memberId,
                                              @Param("type") AgreementType type);

    /** 약관 동의 일괄 저장 (회원가입 시) */
    void insertAll(@Param("memberId") Long memberId,
                   @Param("agreements") List<Agreement> agreements);

    /** 특정 약관 동의 여부 변경 (마케팅 동의/철회) */
    void updateAgreed(@Param("memberId") Long memberId,
                      @Param("type") AgreementType type,
                      @Param("agreed") boolean agreed);

    /** 회원 탈퇴 시 전체 삭제 */
    void deleteByMemberId(Long memberId);
}