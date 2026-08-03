package com.team.independence.compare.dto;

import lombok.Getter;
import lombok.Setter;

/** 거래 유형별 인원 수. 집계 쿼리 결과를 담는 중간 객체다. */
@Getter
@Setter
public class DealTypeCount {

    /** JEONSE / WOLSE */
    private String dealType;

    private int count;
}