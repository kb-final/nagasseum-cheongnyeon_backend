package com.team.independence.asset.service;

import com.team.independence.asset.dto.account.LoanAccountDetailItem;
import com.team.independence.asset.mapper.LoanAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanAccountServiceImpl implements LoanAccountService {

    private final LoanAccountMapper loanAccountMapper;

    @Override
    @Transactional(readOnly = true)
    public List<LoanAccountDetailItem> getLoanAccounts(Long memberId) {
        return loanAccountMapper.findAllDetailsByMemberId(memberId);
    }
}
