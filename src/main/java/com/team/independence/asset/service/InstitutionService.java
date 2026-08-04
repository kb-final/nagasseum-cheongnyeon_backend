package com.team.independence.asset.service;

import com.team.independence.asset.domain.Institution;
import com.team.independence.asset.dto.OrganizationResponse;

import java.util.List;

public interface InstitutionService {
    List<OrganizationResponse> getOrganizations(Long memberId);
    Institution getByCode(String code);
}
