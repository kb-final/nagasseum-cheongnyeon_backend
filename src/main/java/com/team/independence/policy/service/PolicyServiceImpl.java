package com.team.independence.policy.service;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.policy.domain.Policy;
import com.team.independence.policy.dto.PolicyDetailResponse;
import com.team.independence.policy.dto.PolicyListRequest;
import com.team.independence.policy.dto.PolicySummaryResponse;
import com.team.independence.policy.mapper.PolicyMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyMapper policyMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getList(PolicyListRequest request) {
        return policyMapper.findList(request).stream()
                .map(PolicySummaryResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countList(PolicyListRequest request) {
        return policyMapper.countList(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyDetailResponse getDetail(Long id) {
        Policy policy = policyMapper.findById(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.POLICY_NOT_FOUND);
        }
        return PolicyDetailResponse.from(policy);
    }
}
