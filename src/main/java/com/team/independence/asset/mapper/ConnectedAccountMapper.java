package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.ConnectedAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConnectedAccountMapper {
    ConnectedAccount findByMemberId(Long memberId);
    void insert(ConnectedAccount connectedAccount);
}
