package com.team.independence.compare.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 코호트 월 저축액의 주요 구간.
 *
 * <p>가운데 50%(25~75 백분위)의 최소·최대다. 양 끝 극단값에 휘둘리지 않으면서
 * "이 정도가 흔하다"를 보여주기 위한 값이다.
 */
@Getter
@Setter
public class SavingRangeResult {

    private Long cohortRangeMin;
    private Long cohortRangeMax;
}