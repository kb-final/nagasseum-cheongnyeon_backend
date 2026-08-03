package com.team.independence.compare.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 달성률 구간별 인원 수.
 *
 * <p>인원이 0인 구간은 쿼리 결과에 아예 나오지 않는다.
 * 빠진 구간을 0으로 채우는 일은 서비스 계층에서 한다.
 */
@Getter
@Setter
public class AchievementBucketCount {

    /** 0=0~10%, 1=10~20% ... 8=80~100% */
    private int bucketIndex;

    private int count;
}