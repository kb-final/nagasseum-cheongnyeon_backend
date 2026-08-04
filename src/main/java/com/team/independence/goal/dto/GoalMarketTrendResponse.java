package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

/**
 * 홈 화면 「매물 시세 변화」 카드용 데이터.
 *
 * <p>표시용 가공(평수 라벨 조합, 연월 문자열 포맷, 금액 단위 변환 등)은 하지 않고 raw 값만 내려준다.
 * FE가 화면에 맞게 포맷한다.
 */
@Getter
@Builder
public class GoalMarketTrendResponse {

    private String regionName;
    private HousingType housingType;
    private DealType dealType;
    private Integer areaMin;
    private Integer areaMax;

    /** 실거래 집계 기준 최신 연월 */
    private YearMonth updatedYm;

    /** currentMiddleAmount - initialMiddleAmount (부호 포함) */
    private Long changeAmount;

    /** 내 목표 금액 (설정 시점에 고정, 불변) */
    private Long targetAmount;

    /** 목표 설정 당시 실거래 중앙값 */
    private Long initialMiddleAmount;

    /** 현재 실거래 중앙값 */
    private Long currentMiddleAmount;

    /** 목표를 유지할 때의 도달 예상 시점. 재계산하지 않고 goal.target_date를 그대로 반환한다(홈 화면에 노출되는 목표 시점과 동일 값 유지). */
    private YearMonth maintainEta;

    /** 목표 금액 대신 현재 시세(currentMiddleAmount)를 반영했을 때의 예상 도달 시점. 상한 내 도달 불가면 null */
    private YearMonth reflectEta;
}