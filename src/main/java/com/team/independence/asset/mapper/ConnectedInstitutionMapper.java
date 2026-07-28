package com.team.independence.asset.mapper;

import com.team.independence.asset.domain.ConnectedInstitution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConnectedInstitutionMapper {
    void insert(ConnectedInstitution connectedInstitution);
}
