package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.AssetSummary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetSummaryMapper {
    AssetSummary findByMemberId(Long memberId);
    void upsert(AssetSummary assetSummary);
}
