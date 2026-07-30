package com.team.independence.property.domain;

/**
 * 전월세 거래 유형.
 *
 * <p>MyBatis 기본 EnumTypeHandler가 상수명을 그대로 저장/조회하므로
 * 상수명 = deal_type 컬럼 값이다. 이름을 바꾸면 기존 데이터와 어긋난다.
 *
 * <p>국토부가 내려주는 값이 아니라 월세액에서 파생시키는 값이다.
 */
public enum DealType {
    /** 전세 */
    JEONSE,
    /** 월세 */
    WOLSE;

    /** 월세가 0이면 전세 거래다. */
    public static DealType from(long monthlyRent) {
        return monthlyRent == 0 ? JEONSE : WOLSE;
    }
}
