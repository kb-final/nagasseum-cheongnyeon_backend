package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.manual.ManualAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ManualAssetMapper {
    void insert(ManualAsset manualAsset);
    void update(ManualAsset manualAsset);
    void delete(Long id);
    ManualAsset findById(Long id);
    List<ManualAsset> findAllByMemberId(Long memberId);

    /** 회원이 수동입력한 자산(manual_assets) 합계(원). 없으면 0. */
    Long sumAmountByMemberId(@Param("memberId") Long memberId);
}
