package com.team.independence.compare.dto;

import lombok.Getter;
import lombok.Setter;

/** 희망 지역별 인원 수. 집계 쿼리 결과를 담는 중간 객체다. */
@Getter
@Setter
public class RegionCount {

    /** 법정동코드 앞 5자리 */
    private String regionCode;

    /** 시군구명(region 테이블 조인) */
    private String regionName;

    private int count;
}