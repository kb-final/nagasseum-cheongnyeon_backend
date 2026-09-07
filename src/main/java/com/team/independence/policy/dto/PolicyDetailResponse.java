package com.team.independence.policy.dto;

import com.team.independence.policy.domain.Policy;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyDetailResponse {

    private Long id;
    private String policyName;
    private String policySummary;
    private String largeCategory;
    private String mediumCategory;
    private String providingOrgName;
    private String applyPeriodType;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private Integer minAge;
    private Integer maxAge;
    private String applyUrl;
    private String targetDescription;
    private String benefitDescription;

    public static PolicyDetailResponse from(Policy policy) {
        return PolicyDetailResponse.builder()
                .id(policy.getId())
                .policyName(policy.getPolicyName())
                .policySummary(policy.getPolicySummary())
                .largeCategory(policy.getLargeCategory())
                .mediumCategory(policy.getMediumCategory())
                .providingOrgName(policy.getProvidingOrgName())
                .applyPeriodType(policy.getApplyPeriodType())
                .applyStartDate(policy.getApplyStartDate())
                .applyEndDate(policy.getApplyEndDate())
                .minAge(policy.getMinAge())
                .maxAge(policy.getMaxAge())
                .applyUrl(policy.getApplyUrl())
                .targetDescription(policy.getTargetDescription())
                .benefitDescription(policy.getBenefitDescription())
                .build();
    }
}
