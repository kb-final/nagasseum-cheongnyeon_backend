package com.team.independence.member.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.member.domain.Member;
import com.team.independence.member.dto.MemberResponse;
import com.team.independence.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ★ 팀원들이 복제할 표준 패턴 ★
 *  - 생성자 주입(@RequiredArgsConstructor + final)
 *  - 트랜잭션은 이 계층에만 (@Transactional)
 *  - 없는 데이터는 BusinessException으로 처리 (null 반환 금지)
 *  - 도메인 → DTO 변환 후 반환
 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;

    @Override
    @Transactional(readOnly = true)
    public MemberResponse getMember(Long id) {
        Member member = memberMapper.findById(id);
        if (member == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return MemberResponse.from(member);
    }
}
