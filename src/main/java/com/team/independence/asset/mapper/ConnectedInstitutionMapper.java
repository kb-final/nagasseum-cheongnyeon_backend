package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.codef.ConnectedInstitution;
import com.team.independence.asset.dto.connection.LinkedOrganizationResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConnectedInstitutionMapper {
    void insert(ConnectedInstitution connectedInstitution);
    List<ConnectedInstitution> findAllByConnectedAccountId(Long connectedAccountId);
    List<LinkedOrganizationResponse> findAllWithOrganizationByConnectedAccountId(Long connectedAccountId);
    ConnectedInstitution findByConnectedAccountIdAndInstitutionCode(@Param("connectedAccountId") Long connectedAccountId, @Param("institutionCode") String institutionCode);
    void deleteByConnectedAccountIdAndInstitutionCode(@Param("connectedAccountId") Long connectedAccountId, @Param("institutionCode") String institutionCode);
    int countByConnectedAccountId(Long connectedAccountId);
    void updateSyncResult(ConnectedInstitution connectedInstitution);
}
