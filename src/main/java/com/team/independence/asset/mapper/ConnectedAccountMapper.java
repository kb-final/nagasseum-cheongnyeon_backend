package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.ConnectedAccount;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ConnectedAccountMapper {
    ConnectedAccount findByMemberId(Long memberId);
    List<Long> findAllMemberIds();
    void insert(ConnectedAccount connectedAccount);
    void updateSyncStatus(ConnectedAccount connectedAccount);
    void deleteById(Long id);
}
