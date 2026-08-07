package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.account.AssetAccount;
import com.team.independence.asset.dto.account.AssetAccountDetailItem;
import com.team.independence.asset.dto.account.AssetAccountQueryItem;
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
    List<AssetAccountDetailItem> findBankAccountDetailsByMemberId(Long memberId);
    List<AssetAccountDetailItem> findStockAccountDetailsByMemberId(Long memberId);
    Long sumCurrentValueByMemberId(@Param("memberId") Long memberId);

    /** 회원이 연동한 자산 계좌 중 주어진 asset_category에 속하는 계좌들의 현재가치 합계(원). 없으면 0. */
    Long sumCurrentValueByMemberIdAndCategories(@Param("memberId") Long memberId,
                                                 @Param("categories") List<String> categories);
}
