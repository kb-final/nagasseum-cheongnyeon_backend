package com.team.independence.policy.service;

import com.team.independence.policy.dto.PolicyDetailResponse;
import com.team.independence.policy.dto.PolicyListRequest;
import com.team.independence.policy.dto.PolicySummaryResponse;
import java.util.List;

public interface PolicyService {

    List<PolicySummaryResponse> getList(PolicyListRequest request);

    long countList(PolicyListRequest request);

    PolicyDetailResponse getDetail(Long id);
}
