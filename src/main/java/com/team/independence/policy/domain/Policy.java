package com.team.independence.policy.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Policy {

    private Long id;
    private String sourceApi;
    private String policyApiId;
    private String largeCategory;
    private String mediumCategory;
    private String providingMethod;
    private String policyName;
    private String policySummary;
    private String targetDescription;
    private String benefitDescription;
    private String providingOrgName;
    private String applyPeriodType;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private String applyUrl;
    private Integer minAge;
    private Integer maxAge;
    private String extra;
    private boolean isActive;
    private String applicableGoalTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public Policy(String sourceApi, String policyApiId, String largeCategory, String mediumCategory,
                  String providingMethod, String policyName, String policySummary,
                  String targetDescription, String benefitDescription, String providingOrgName,
                  String applyPeriodType, LocalDate applyStartDate, LocalDate applyEndDate,
                  String applyUrl, Integer minAge, Integer maxAge, String extra,
                  boolean isActive, String applicableGoalTypes) {
        this.sourceApi = sourceApi;
        this.policyApiId = policyApiId;
        this.largeCategory = largeCategory;
        this.mediumCategory = mediumCategory;
        this.providingMethod = providingMethod;
        this.policyName = policyName;
        this.policySummary = policySummary;
        this.targetDescription = targetDescription;
        this.benefitDescription = benefitDescription;
        this.providingOrgName = providingOrgName;
        this.applyPeriodType = applyPeriodType;
        this.applyStartDate = applyStartDate;
        this.applyEndDate = applyEndDate;
        this.applyUrl = applyUrl;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.extra = extra;
        this.isActive = isActive;
        this.applicableGoalTypes = applicableGoalTypes;
    }
}
