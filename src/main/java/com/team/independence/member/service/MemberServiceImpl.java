package com.team.independence.member.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.IncomeBracket;
import com.team.independence.member.domain.Member;
import com.team.independence.member.domain.OccupationType;
import com.team.independence.member.dto.MemberProfileResponse;
import com.team.independence.member.mapper.AgreementMapper;
import com.team.independence.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;
    private final AgreementMapper agreementMapper;

    @Override
    @Transactional(readOnly = true)
    public MemberProfileResponse getMember(Long id) {
        Member member = memberMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        List<Agreement> agreements = agreementMapper.findByMemberId(id);
        return MemberProfileResponse.from(member, agreements);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getMonthlyIncome(Long id) {
        return memberMapper.findIncomeById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .getMonthlyIncome();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findMemberIdByKakaoId(String kakaoId) {
        return memberMapper.findByKakaoId(kakaoId).map(Member::getId);
    }

    @Override
    @Transactional
    public void updateMember(Long memberId, String nickname, IncomeBracket incomeBracket,
                             Long monthlyIncome, OccupationType occupationType) {
        memberMapper.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Member member = Member.builder()
                .id(memberId)
                .nickname(nickname)
                .incomeBracket(incomeBracket)
                .monthlyIncome(monthlyIncome)
                .occupationType(occupationType)
                .build();
        memberMapper.update(member);
    }

    @Override
    @Transactional
    public Long createMember(String kakaoId, String nickname, LocalDate birthDate, IncomeBracket incomeBracket) {
        memberMapper.findByKakaoId(kakaoId)
                .ifPresent(m -> { throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS); });

        Member member = Member.builder()
                .kakaoId(kakaoId)
                .nickname(nickname)
                .birthDate(birthDate)
                .incomeBracket(incomeBracket)
                .build();
        memberMapper.insert(member);
        return member.getId();
    }
}