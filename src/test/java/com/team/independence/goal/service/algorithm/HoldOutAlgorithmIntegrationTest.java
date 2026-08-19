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
 */
@Disabled("로컬 MySQL + Redis 환경 전용")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class HoldOutAlgorithmIntegrationTest {

    /** 테스트에 쓸 회원 ID — 로컬 DB에 맞게 변경 */
    private static final long TEST_MEMBER_ID = 1L;

    @Autowired
    private HoldOutAlgorithm holdOutAlgorithm;
    @Autowired
    private AssetSummaryService assetSummaryService;
    @Autowired
    private LoanPlanCalculator loanPlanCalculator;

    private MemberFinancialContext buildCtx(long memberId) {
        AssetNetWorthBreakdown netWorth = assetSummaryService.getNetWorthBreakdown(memberId);
        long monthlySaving = assetSummaryService.getMonthlySavingsOrZero(memberId);
        long loanPayment = loanPlanCalculator.calcTotalExistingMonthlyPayment(memberId);
        return new MemberFinancialContext(netWorth, monthlySaving, loanPayment);
    }

    /**
     * request에 조건을 직접 넣어서 실행.
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
        request.setTargetDate(YearMonth.now().plusMonths(24)); // n=24개월 기준

        List<GoalRecommendationResponse.RecommendationItem> result =
                holdOutAlgorithm.recommend(TEST_MEMBER_ID, request, buildCtx(TEST_MEMBER_ID));

        System.out.println("=== HoldOut 결과 ===");
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

        // 데이터가 충분하면 카드가 있어야 함 (로컬 DB 데이터에 따라 달라짐)
        if (!result.isEmpty()) assertNotNull(result.get(0));
    }

    /**
     * request 조건 없이 active goal 기반으로 실행.
     * TEST_MEMBER_ID에 ACTIVE 목표와 GoalHousing이 있어야 함.
     */
    @Test
    void goal_fallback() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        // 조건 미입력 → active goal의 housing 조건 사용

        List<GoalRecommendationResponse.RecommendationItem> result =
                holdOutAlgorithm.recommend(TEST_MEMBER_ID, request, buildCtx(TEST_MEMBER_ID));

        System.out.println("=== HoldOut (goal fallback) 결과 ===");
        if (!result.isEmpty() && result.get(0).getCondition() != null) {
            GoalRecommendationResponse.RecommendationItem item = result.get(0);
            System.out.println("조건   : " + item.getCondition().getRegionName()
                    + " " + item.getCondition().getAreaMin()
                    + "~" + item.getCondition().getAreaMax() + "평");
        } else {
            System.out.println("추천 결과 없음 (active goal 없거나 데이터 부족)");
        }
    }
}
