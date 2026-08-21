package com.team.independence.goal.service.algorithm;

import com.team.independence.asset.dto.summary.AssetNetWorthBreakdown;
import com.team.independence.asset.service.AssetSummaryService;
import com.team.independence.config.RootConfig;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.service.RecommendationAlgorithm.MemberFinancialContext;
import com.team.independence.goal.service.calculator.LoanPlanCalculator;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HoldOut 알고리즘 로컬 수동 실행용 통합 테스트.
 *
 * <p>실행 방법: @Disabled 제거 후 로컬에서 실행
 *
 * <p>사전 조건:
 * <ul>
 *   <li>docker compose up -d (MySQL, Redis)</li>
 *   <li>테스트에 쓸 memberId의 connected_account, asset_account, asset_summary.monthly_savings 존재</li>
 *   <li>rent_transaction에 해당 region/type/deal 최근 6개월 데이터 10건 이상</li>
 * </ul>
 *
 * <p>HoldOut은 Realistic 결과를 기준점으로 동작한다.
 * 통합 테스트에서는 RealisticAlgorithm을 먼저 실행해 결과를 HoldOut에 전달한다.
 */
@Disabled("로컬 MySQL + Redis 환경 전용")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class HoldOutAlgorithmIntegrationTest {

    /** 테스트에 쓸 회원 ID — 로컬 DB에 맞게 변경 */
    private static final long TEST_MEMBER_ID = 1L;

    @Autowired private RealisticAlgorithm realisticAlgorithm;
    @Autowired private HoldOutAlgorithm   holdOutAlgorithm;
    @Autowired private AssetSummaryService assetSummaryService;
    @Autowired private LoanPlanCalculator  loanPlanCalculator;

    private MemberFinancialContext buildCtx(long memberId) {
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long monthlySaving = assetSummaryService.getMonthlySavingsOrZero(memberId);
        return new MemberFinancialContext(netWorth, monthlySaving, loanPlanCalculator.getLoanSchedules(memberId));
    }

    /**
     * Realistic을 먼저 실행한 뒤 그 결과를 HoldOut에 전달한다.
     * regionCode, propertyType, tradeType, sizeMin, sizeMax는 rent_transaction에 실제로 있는 값으로 변경.
     */
    @Test
    void request_조건_직접_지정() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11680");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setSizeMin(20);
        request.setSizeMax(30);
        request.setTargetDate(YearMonth.now().plusMonths(24));

        MemberFinancialContext ctx = buildCtx(TEST_MEMBER_ID);

        // Phase 1: Realistic
        List<GoalRecommendationResponse.RecommendationItem> realisticResult =
                realisticAlgorithm.recommend(TEST_MEMBER_ID, request, ctx);
        GoalRecommendationResponse.RecommendationItem realisticItem =
                realisticResult.isEmpty() ? null : realisticResult.get(0);

        System.out.println("=== Realistic 결과 ===");
        System.out.println(realisticItem != null && realisticItem.getCondition() != null
                ? realisticItem.getCondition().getRegionName()
                    + " / " + realisticItem.getCondition().getHousingType()
                    + " / " + realisticItem.getCondition().getDealType()
                    + " / " + realisticItem.getCondition().getAreaMin()
                    + "~" + realisticItem.getCondition().getAreaMax() + "평"
                : "soft-fail (condition null)");

        // Phase 2: HoldOut (Realistic 결과를 기준점으로 사용)
        List<GoalRecommendationResponse.RecommendationItem> result =
                holdOutAlgorithm.recommend(TEST_MEMBER_ID, request, ctx, realisticItem);

        System.out.println("\n=== HoldOut 결과 ===");
        if (!result.isEmpty()) {
            GoalRecommendationResponse.RecommendationItem item = result.get(0);
            System.out.println("type   : " + item.getType());
            System.out.println("조건   : " + (item.getCondition() == null ? "null(soft-fail)"
                    : item.getCondition().getRegionName()
                    + " / " + item.getCondition().getHousingType()
                    + " / " + item.getCondition().getDealType()
                    + " / " + item.getCondition().getAreaMin()
                    + "~" + item.getCondition().getAreaMax() + "평"));
            System.out.println("loanX  : " + item.getLoanX());
            System.out.println("loanO  : " + item.getLoanO());
        } else {
            System.out.println("추천 결과 없음 (빈 목록)");
        }

        if (!result.isEmpty()) assertNotNull(result.get(0));
    }
}
