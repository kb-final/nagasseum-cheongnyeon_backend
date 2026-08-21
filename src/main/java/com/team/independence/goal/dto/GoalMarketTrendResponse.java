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

    /** currentMiddleAmount 산출 기준 최신 실거래 연월 */
    private YearMonth updatedYm;

    /** updatedYm 기준 최신 실거래 데이터로 predictionTargetYm 시점을 다시 예측한 몬테카를로 P50 시세.
     *  predictionTargetYm이 이미 지났거나(months&lt;=0) 가격 모델을 만들 실거래 표본이 부족하면 null. */
    private Long latestPredictedMarketAmount;

    /** initialMiddleAmount·latestPredictedMarketAmount가 공통으로 예측하는 목표 연월(= goal.target_date).
     *  예측 결과가 아니라 예측 대상 시점이므로 latestPredictedMarketAmount가 null이어도 항상 채워진다. */
    private YearMonth predictionTargetYm;

    /** latestPredictedMarketAmount - initialMiddleAmount (부호 포함). latestPredictedMarketAmount가 null이면 null */
    private Long predictionChangeAmount;

    /** 내 목표 금액 (설정 시점에 고정, 불변) */
    private Long targetAmount;

    /** 목표 진단 당시 계산한, predictionTargetYm(목표 시점)에 대한 몬테카를로 P50 예측 시세 (설정 시점에 고정) */
    private Long initialMiddleAmount;

    /** updatedYm 기준 현재 실거래 중앙값 */
    private Long currentMiddleAmount;

    /** 목표를 유지할 때의 도달 예상 시점. 재계산하지 않고 goal.target_date를 그대로 반환한다(홈 화면에 노출되는 목표 시점과 동일 값 유지). */
    private YearMonth maintainEta;

    /** 목표 금액 대신 현재 시세(currentMiddleAmount)를 반영했을 때의 예상 도달 시점. 상한 내 도달 불가면 null */
    private YearMonth reflectEta;
}