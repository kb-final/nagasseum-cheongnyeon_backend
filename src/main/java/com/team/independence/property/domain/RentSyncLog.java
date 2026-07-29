package com.team.independence.property.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * (지역, 연월, 주택유형) 단위 수집 이력.
 * 최초 수집/증분 수집을 코드로 분기하지 않고, 이 이력의 성공 여부로 판단한다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RentSyncLog {

    private String regionCode;
    private String dealYm;
    private String housingType;
    /** 래퍼 타입 고정: primitive boolean이면 Lombok이 setSuccess()를 만들어 is_success 매핑이 깨진다 */
    private Boolean isSuccess;
    private Integer insertedCnt;
}
