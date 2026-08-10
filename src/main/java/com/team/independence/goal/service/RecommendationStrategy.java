package com.team.independence.goal.service;

import com.team.independence.goal.dto.RecommendationType;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.ScoringContext;
import java.util.Optional;

/**
 * 추천 테마 하나를 담당하는 전략 인터페이스.
 *
 * <p>새 테마를 추가할 때는 이 인터페이스를 구현한 {@code @Service} 클래스를 추가하면 된다.
 * {@link RecommendationScoringEngine} 구현체는 {@code List<RecommendationStrategy>}를
 * 주입받아 등록된 전략을 모두 실행하므로, 기존 코드를 수정할 필요가 없다.
 *
 * <p>구현 예시:
 * <pre>
 * {@literal @}Service
 * public class PreferenceStrategy implements RecommendationStrategy {
 *     public RecommendationType getType() { return RecommendationType.PREFERENCE; }
 *     public Optional<RecommendationItem> recommend(ScoringContext ctx) { ... }
 * }
 * </pre>
 */
public interface RecommendationStrategy {

    /** 이 전략이 담당하는 추천 테마 */
    RecommendationType getType();

    /**
     * 후보 목록을 스코링해 이 전략의 최고 점수 추천 결과를 반환한다.
     * 적합한 후보가 없으면 {@link Optional#empty()}를 반환한다.
     */
    Optional<GoalRecommendationResponse.RecommendationItem> recommend(ScoringContext context);
}
