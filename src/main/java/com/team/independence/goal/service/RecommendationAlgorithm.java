package com.team.independence.goal.service;

import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import java.util.Optional;

/**
 * 추천 알고리즘 하나. 구현체 하나가 곧 추천 카드 한 장을 만든다.
 *
 * <p>구현체들은 서로 독립적이다. 공통 후보군도, 공통 스코링 공식도 없다.
 * 어떤 데이터를 조회하든 어떤 로직으로 대안을 고르든 구현체 재량이며,
 * 필요한 매퍼·서비스는 직접 주입받아 쓰면 된다. 다른 구현체와 맞출 필요가 없다.
 *
 * <p>지켜야 할 공통 계약은 두 가지뿐이다.
 * <ul>
 *   <li>입력: {@code memberId}와 {@link GoalRecommendationRequest}만 받는다</li>
 *   <li>출력: {@link GoalRecommendationResponse.RecommendationItem} 한 건을 만든다</li>
 * </ul>
 *
 * <p>단, 응답의 loanX / loanO 플랜은 직접 계산하지 말고 반드시 {@link LoanPlanCalculator}에
 * 위임한다. 카드 여러 장에 나란히 노출되는 금액이라 알고리즘마다 저축 계산식이 다르면
 * 사용자가 서로 비교할 수 없기 때문이다. 알고리즘은 "어떤 조건의 주거를, 언제까지"만 정하면 된다.
 *
 * <p>추천 알고리즘을 추가·제거할 때는 이 인터페이스 구현체만 만들고 지우면 된다.
 * {@code GoalRecommendationService}가 {@code List<RecommendationAlgorithm>}을 주입받아
 * 등록된 구현체를 모두 실행하므로 기존 코드는 수정하지 않는다.
 *
 * <p>구현 예시:
 * <pre>
 * {@literal @}Service
 * {@literal @}RequiredArgsConstructor
 * public class ValueAlgorithm implements RecommendationAlgorithm {
 *
 *     private final LoanPlanCalculator loanPlanCalculator;
 *     // 그 외 이 알고리즘에만 필요한 의존성은 자유롭게 추가
 *
 *     public Optional&lt;RecommendationItem&gt; recommend(long memberId, GoalRecommendationRequest req) {
 *         // 1. 자기 방식대로 추천할 주거 조건과 목표 시점을 정한다
 *         // 2. loanPlanCalculator.calculate(memberId, 필요금액, 목표시점)으로 플랜 두 개를 받는다
 *         // 3. type(AlgorithmType.VALUE), title, reason, condition을 채워 조립한다
 *     }
 * }
 * </pre>
 */
public interface RecommendationAlgorithm {

    /**
     * 회원과 요청 조건을 바탕으로 추천 대안 한 건을 만든다.
     *
     * <p>결과의 {@code type}에는 이 알고리즘에 해당하는
     * {@link com.team.independence.goal.dto.AlgorithmType} 값을 채워야 한다.
     *
     * @param memberId 요청 회원 ID
     * @param request  추천 요청 파라미터
     * @return 추천 대안. 제시할 만한 대안이 없으면 {@link Optional#empty()}
     */
    Optional<GoalRecommendationResponse.RecommendationItem> recommend(
            long memberId, GoalRecommendationRequest request);
}
