package com.team.independence.property.domain;

/**
 * 국토부 전월세 실거래 4종 주택유형.
 *
 * <p>MyBatis 기본 EnumTypeHandler가 상수명을 그대로 저장/조회하므로
 * 상수명 = housing_type 컬럼 값이다. 이름을 바꾸면 기존 데이터와 어긋난다.
 */
public enum HousingType {
    /** 아파트 */
    APT,
    /** 연립다세대 */
    ROW_HOUSE,
    /** 오피스텔 */
    OFFICETEL,
    /** 단독다가구 */
    DETACHED
}
