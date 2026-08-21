package com.team.independence.goal.service;

import com.team.independence.goal.dto.MonteCarloResponse;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;

public interface MonteCarloService {

    /**
     * 저장된 목표에 대해 시뮬레이션을 실행한다. 목표 상세 화면(엔드포인트)용
     *
     * P(0): 목표 설정 당시 기록된 중앙값(targetRentMiddleAmount)
     */
    MonteCarloResponse simulate(Long memberId, Long goalId);

    /**
     * 주거 조건과 예산 파라미터를 직접 받아 시뮬레이션을 실행한다. (추천 알고리즘 내부용)
     *
     * goalId 없이 호출할 수 있어, 목표 저장 전 추천 흐름에서 사용한다.
     * PriceModel(μ, σ) 산출은 내부에서 처리하므로 호출자는 주거 조건만 넘기면 된다.
     *
     * @param housing PriceModel 산출에 필요한 주거 조건 (지역 / 유형 / 평수)
     * @param initialPrice P(0): 기준 시점의 주택 가격 (현재 시세 중앙값, 원)
     * @param budgetAtT T 시점의 예상 예산 (BudgetCalculator 결과, 원)
     * @param months 시뮬레이션 기간 (개월)
     * @return p5 / p50 / p95 가격 분위값과 성공 확률
     */
    MonteCarloEngine.Result simulate(PriceModelRequest housing, long initialPrice, long budgetAtT, int months);

    /**
     * 이미 산출된 PriceModelResponse(μ, σ)를 그대로 받아 시뮬레이션만 실행한다.
     *
     * <p>Realistic·HoldOut처럼 여러 조합의 PriceModel을 배치로 미리 준비해 두는 경로에서 사용한다.
     * 조합마다 priceModelService.estimate를 재호출하지 않아 Redis 캐시 검사·DB 왕복이 사라진다.
     */
    MonteCarloEngine.Result simulate(PriceModelResponse priceModel, long initialPrice, long budgetAtT, int months);
}
