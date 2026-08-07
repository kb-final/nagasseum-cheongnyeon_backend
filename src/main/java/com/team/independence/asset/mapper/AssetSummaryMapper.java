package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.summary.AssetSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetSummaryMapper {
    AssetSummary findByMemberId(Long memberId);
    void upsert(AssetSummary assetSummary);
    void upsertMonthlySavings(@Param("memberId") Long memberId, @Param("monthlySavings") Long monthlySavings);
}
