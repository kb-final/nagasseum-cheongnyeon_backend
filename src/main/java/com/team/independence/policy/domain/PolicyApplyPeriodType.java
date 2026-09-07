package com.team.independence.policy.domain;

public enum PolicyApplyPeriodType {

    ONGOING,  // 상시 (aplyPrdSeCd=0057001)
    SPECIFIC,  // 특정기간 (aplyPrdSeCd=0057002)
    CLOSED;  // 마감 (aplyPrdSeCd=0057003)

    public static PolicyApplyPeriodType fromCode(String code) {
        if (code == null) return ONGOING;
        switch (code.trim()) {
            case "0057001": return ONGOING;
            case "0057002": return SPECIFIC;
            case "0057003": return CLOSED;
            default: return ONGOING;
        }
    }
}
