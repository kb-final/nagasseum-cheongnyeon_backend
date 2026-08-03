package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.ManualAsset;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ManualAssetMapper {
    void insert(ManualAsset manualAsset);
    void update(ManualAsset manualAsset);
    void delete(Long id);
    ManualAsset findById(Long id);
    List<ManualAsset> findAllByMemberId(Long memberId);
}
