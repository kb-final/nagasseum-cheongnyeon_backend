package com.team.independence.compare.domain;

public enum MonthlyIncomeBracket {
    UNDER_1M (0L,         1_000_000L),
    M1_TO_2M (1_000_000L, 2_000_000L),
    M2_TO_3M (2_000_000L, 3_000_000L),
    M3_TO_4M (3_000_000L, 4_000_000L),
    M4_TO_5M (4_000_000L, 5_000_000L),
    OVER_5M  (5_000_000L, Long.MAX_VALUE);

    public final long min;
    public final long max;

    MonthlyIncomeBracket(long min, long max) {
        this.min = min;
        this.max = max;
    }

    public static MonthlyIncomeBracket of(long monthlyIncome) {
        for (MonthlyIncomeBracket b : values()) {
            if (monthlyIncome >= b.min && monthlyIncome < b.max) return b;
        }
        return OVER_5M;
    }
}
