package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.account.CardAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CardAccountMapper {
    void insertAll(@Param("list") List<CardAccount> list);
    void deleteByConnectedInstitutionId(Long connectedInstitutionId);
    List<CardAccount> findByMemberId(Long memberId);
}
