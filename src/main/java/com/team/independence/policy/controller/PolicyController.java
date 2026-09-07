package com.team.independence.policy.controller;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.policy.dto.PolicyDetailResponse;
import com.team.independence.policy.dto.PolicyListRequest;
import com.team.independence.policy.dto.PolicySummaryResponse;
import com.team.independence.policy.service.PolicyService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getList(@ModelAttribute PolicyListRequest request) {
        List<PolicySummaryResponse> policies = policyService.getList(request);
        long totalCount = policyService.countList(request);

        Map<String, Object> body = new HashMap<>();
        body.put("policies", policies);
        body.put("totalCount", totalCount);
        body.put("page", request.getPage());
        body.put("size", request.getSize());
        return ApiResponse.ok(body);
    }

    @GetMapping("/{id}")
    public ApiResponse<PolicyDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(policyService.getDetail(id));
    }
}
