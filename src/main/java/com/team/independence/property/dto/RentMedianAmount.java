package com.team.independence.property.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 중앙값 집계에 필요한 금액 두 개만 담는 조회 결과.
 *
 * <p>분위값은 보증금·월세를 각각 따로 정렬해야 나오므로 집계된 값이 아니라 원본 행을 받아온다.
 * RentTransaction 전체를 읽으면 원본 JSON 컬럼까지 딸려 오기 때문에 별도 프로젝션을 둔다.
 */
@Getter
@Setter
@NoArgsConstructor
public class RentMedianAmount {

    /** 보증금(원) */
    private long deposit;

    /** 월세(원). 전세 거래는 0 */
    private long monthlyRent;
}
