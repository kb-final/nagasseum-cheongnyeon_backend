package com.team.independence.member.service;

import com.team.independence.member.domain.Agreement;

import java.util.List;

public interface AgreementService {

    /** 회원의 전체 약관 동의 현황 조회 */
    List<Agreement> getAgreements(Long memberId);

    /** 회원가입 시 동의 항목 저장 (NOTIFICATION, COMPARE_DATA) */
    void saveAll(Long memberId, List<Agreement> agreements);

    /** 동의 항목 일괄 변경 (저장하기 버튼) */
    void updateAll(Long memberId, List<Agreement> agreements);

    /** 회원 탈퇴 시 전체 삭제 */
    void deleteByMemberId(Long memberId);
}