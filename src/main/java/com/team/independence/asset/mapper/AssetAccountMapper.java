package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.AssetAccount;
import com.team.independence.asset.dto.AssetAccountDetailItem;
import com.team.independence.asset.dto.AssetAccountQueryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssetAccountMapper {
    void insert(AssetAccount assetAccount);
    void insertAll(@Param("list") List<AssetAccount> list);
    void deleteByConnectedInstitutionId(Long connectedInstitutionId);
    List<AssetAccount> findByConnectedInstitutionId(Long connectedInstitutionId);
    List<AssetAccountQueryItem> findWithInstitutionByMemberId(Long memberId);
    List<AssetAccountDetailItem> findAllDetailsByMemberId(Long memberId);
    Long sumCurrentValueByMemberId(Long memberId);
}
