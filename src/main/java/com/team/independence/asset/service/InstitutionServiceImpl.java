package com.team.independence.asset.service;

import com.team.independence.asset.domain.codef.Institution;
import com.team.independence.asset.dto.connection.OrganizationResponse;
import com.team.independence.asset.mapper.InstitutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstitutionServiceImpl implements InstitutionService {

    private final InstitutionMapper institutionMapper;

    @Override
    public List<OrganizationResponse> getOrganizations(Long memberId) {
        return institutionMapper.findAllActiveWithConnectionStatus(memberId).stream()
                .map(OrganizationResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public Institution getByCode(String code) {
        return institutionMapper.findByCode(code);
    }
}
