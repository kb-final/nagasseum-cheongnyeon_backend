package com.team.independence.asset.service;

import com.team.independence.asset.dto.account.LoanAccountDetailItem;

import java.util.List;

public interface LoanAccountService {
    List<LoanAccountDetailItem> getLoanAccounts(Long memberId);
}
