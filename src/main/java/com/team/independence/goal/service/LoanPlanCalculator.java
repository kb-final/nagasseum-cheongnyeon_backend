package com.team.independence.goal.service;

import com.team.independence.goal.dto.LoanPlans;
import java.time.YearMonth;

/**
 * 추천 대안에 붙는 저축·대출 플랜 계산 — 모든 추천 알고리즘이 공유하는 유일한 계산기.
 *
 * <p>알고리즘들은 서로 다른 방식으로 "어떤 주거를, 언제까지"만 정하고,
 * 그 뒤에 붙는 산수(대출 한도, 자력으로 모을 금액, 월 저축액)는 전부 여기로 위임한다.
 * 카드 4장에 나란히 노출되는 금액이라 알고리즘마다 식이 다르면 사용자가 비교할 수 없기 때문이다.
 *
 * <p>계산 가정
 * <ul>
 *   <li>대출: DSR 40% 한도, 연 3.5% 30년 원리금균등상환</li>
 *   <li>저축: 연 5% 복리({@code GoalServiceImpl.ANNUAL_INTEREST_RATE}와 동일 기준)</li>
 *   <li>소득은 {@code member.monthly_income}, 보유 자산은 순자산 분해 결과를 사용한다</li>
 * </ul>
 *
 * <p>{@code GoalServiceImpl.diagnose()}의 계산이 "월 저축액 → 달성 가능한 예산"인 정방향이라면,
 * 이 계산기는 "필요 금액 → 그에 필요한 월 저축액"인 역방향이다.
 */
public interface LoanPlanCalculator {

    /**
     * 목표 금액과 목표 시점으로 대출 없는 플랜과 대출 낀 플랜을 함께 계산한다.
     *
     * <p>대출 낀 플랜은 DSR 한도만큼을 목표 금액에서 차감한 뒤 나머지를 자력으로 모으는 것으로 잡는다.
     * DSR 한도가 0이면(소득 미등록·기존 대출 과다) loanO는 null이 된다.
     *
     * @param memberId       요청 회원 ID (소득·자산 조회용)
     * @param requiredAmount 목표 주거를 얻는 데 필요한 금액 (원). 보통 실거래 보증금 중앙값
     * @param targetDate     목표 시점
     * @return 플랜 한 쌍
     */
    LoanPlans calculate(long memberId, long requiredAmount, YearMonth targetDate);

    /**
     * DSR 40% 기준 최대 대출 가능액을 계산한다.
     *
     * <p>{@link #calculate}가 내부적으로 쓰는 값이지만, 대안을 고르기 전에 예산 상한을 알아야 하는
     * 알고리즘(예: 소득 대비 현실적인 매물만 추리는 경우)이 따로 호출할 수 있도록 열어 둔다.
     *
     * @param memberId 요청 회원 ID
     * @return 신규 대출 가능 최대 금액 (원). 한도가 없으면 0
     */
    long calcMaxLoanAmount(long memberId);
}
