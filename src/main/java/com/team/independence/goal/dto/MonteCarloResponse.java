package com.team.independence.goal.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonteCarloResponse {

    // 목표 식별
    private Long goalId;
    private String regionCode;
    private String regionName;
    private HousingType housingType;
    private DealType dealType;

    // 시뮬레이션 기간
    private int months;
    private YearMonth targetDate;

    // 가격 모델 파라미터 (PriceModel 산출값)
    private double annualDrift; // μ값: 몬테카를로 drift 입력값
    private double cagr;  // exp(μ) - 1: 표시용 연 상승률
    private double annualVol;  // σ: 몬테카를로 volatility 입력값

    // 현재 상태
    private long initialPrice;  // P(0) = 목표 설정 당시 실거래 중앙값
    private long currentBudget;  // 현재 순자산 (이자 적용 자산 + 이자 적용하지 않은 자산)

    // 시뮬레이션 결과
    private long budgetAtT;  // T 시점 결정적 예상 예산
    private long priceP5;  // P(T) 5th percentile (낙관 시나리오의 하방)
    private long priceP50;  // P(T) 중앙값
    private long priceP95;  // P(T) 95th percentile (비관 시나리오)
    private double successProbability;  // P(budgetAtT >= P(T)) — 목표 달성 확률
}
