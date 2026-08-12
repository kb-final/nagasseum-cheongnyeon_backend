package com.team.independence.goal.service;

import com.team.independence.goal.dto.MonteCarloResponse;

public interface MonteCarloService {

    /**
     * 회원의 목표에 대해 GBM 몬테카를로 시뮬레이션을 실행한다.
     *
     * PriceModel(μ, σ)을 목표의 주거 조건으로 산출하고,
     * P(0) = 목표 설정 당시 중앙값 기준으로 T 시점의 가격 분포를 시뮬레이션한다.
     * 동시에 BudgetCalculator로 T 시점 예상 예산을 결정적으로 계산 후
     * 목표 달성 확률(successProbability)을 함께 반환한다.
     *
     * @param memberId 요청 회원 ID
     * @param goalId 시뮬레이션할 목표 ID
     * @return 시뮬레이션 결과 (가격 분위값, 달성 확률 등)
     */
    MonteCarloResponse simulate(Long memberId, Long goalId);
}
