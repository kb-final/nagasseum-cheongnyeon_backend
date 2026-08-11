package com.team.independence.goal.service;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;

/**
 * n개월 후 예상 예산 계산 — HoldOut 알고리즘 등 추천 영역에서 공유하는 계산기.
 *
 * <p>GoalServiceImpl 내부의 동일한 공식을 추출한 것이다.
 * GoalServiceImpl은 아직 이 인터페이스를 참조하지 않으며, 추후 협의 후 전환한다.
 *
 * <p>계산 가정
 * <ul>
 *   <li>예적금(interestBearingAssets): 연 5% 복리 거치식 성장</li>
 *   <li>기타 자산(flatRecognizedAssets): 원금 그대로 인정</li>
 *   <li>월 저축: 연 5% 복리 적립식 미래가치 (ordinary annuity)</li>
 * </ul>
 */
public interface BudgetCalculator {

    /**
     * n개월 후 예상 총 예산을 계산한다.
     *
     * @param netWorth      순자산 구성 (예적금 / 기타 자산)
     * @param monthlySaving 월 저축액 (원)
     * @param months        현재로부터 경과 개월수
     * @return 예상 총 예산 (원)
     */
    long calculate(AssetNetWorthBreakdown netWorth, long monthlySaving, long months);

    /**
     * 예산이 목표 금액에 도달하는 최소 개월수를 탐색한다.
     *
     * @param netWorth      순자산 구성
     * @param monthlySaving 월 저축액 (원)
     * @param targetAmount  목표 금액 (원)
     * @return 도달 최소 개월수. 저축액이 0 이하이거나 상한 내에 도달하지 못하면 null
     */
    Long monthsToReach(AssetNetWorthBreakdown netWorth, long monthlySaving, long targetAmount);
}
