package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.account.LoanAccount;
import com.team.independence.asset.dto.account.LoanAccountDetailItem;
import com.team.independence.asset.dto.account.LoanAccountQueryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LoanAccountMapper {
    void insert(LoanAccount loanAccount);
    void insertAll(@Param("list") List<LoanAccount> list);
    void deleteByConnectedInstitutionId(Long connectedInstitutionId);
    List<LoanAccount> findByConnectedInstitutionId(Long connectedInstitutionId);
    List<LoanAccountQueryItem> findWithInstitutionByMemberId(Long memberId);
    List<LoanAccountDetailItem> findAllDetailsByMemberId(Long memberId);
    Long sumLoanBalanceByMemberId(@Param("memberId") Long memberId);

}
