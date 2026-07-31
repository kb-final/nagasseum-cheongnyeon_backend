package com.team.independence.member.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.member.domain.Agreement;
import com.team.independence.member.mapper.AgreementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgreementServiceImpl implements AgreementService {

    private final AgreementMapper agreementMapper;

    @Override
    public List<Agreement> getAgreements(Long memberId) {
        return agreementMapper.findByMemberId(memberId);
    }

    @Override
    @Transactional
    public void saveAll(Long memberId, List<Agreement> agreements) {
        agreementMapper.insertAll(memberId, agreements);
    }

    @Override
    @Transactional
    public void updateAll(Long memberId, List<Agreement> agreements) {
        for (Agreement agreement : agreements) {
            agreementMapper.findByMemberIdAndType(memberId, agreement.getAgreementType())
                    .orElseThrow(() -> new BusinessException(ErrorCode.AGREEMENT_NOT_FOUND));
            agreementMapper.updateAgreed(memberId, agreement.getAgreementType(), agreement.isAgreed());
        }
    }

    @Override
    @Transactional
    public void updateOne(Long memberId, Agreement.AgreementType type, boolean agreed) {
        agreementMapper.findByMemberIdAndType(memberId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGREEMENT_NOT_FOUND));
        agreementMapper.updateAgreed(memberId, type, agreed);
    }

    @Override
    @Transactional
    public void deleteByMemberId(Long memberId) {
        agreementMapper.deleteByMemberId(memberId);
    }
}
