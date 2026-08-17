package com.team.independence.goal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.independence.config.RootConfig;
import com.team.independence.goal.dto.GoalRecommendationRequest;
import com.team.independence.goal.dto.GoalRecommendationResponse;
import com.team.independence.goal.dto.GoalRecommendationResponse.RecommendationItem;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * GoalRecommendationService 통합 테스트 — 세 알고리즘(HOLD_OUT / REALISTIC / PREFERENCE)을 한 번에 실행.
 *
 * <p>사전 조건
 * <ol>
 *   <li>docker compose up -d (MySQL, Redis)</li>
 *   <li>test-mock-data.sql 실행 → member(1, 2) 및 자산 데이터 적재</li>
 *   <li>rent_transaction에 서울 최근 6개월 실거래가 적재
 *       (RentTransactionSyncServiceIntegrationTest#서울_최근6개월_적재 실행)</li>
 * </ol>
 */
//@Disabled("로컬 DB 환경 전용 — 위 사전 조건 충족 후 @Disabled 제거하고 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class GoalRecommendationServiceIntegrationTest {

    /** test-mock-data.sql 로 삽입한 회원 ID */
    private static final long MEMBER_NO_LOAN = 1L;   // 대출 없음, 월저축 300만, 예금 3000만
    private static final long MEMBER_WITH_LOAN = 2L; // 대출 1억,  월저축 200만, 예금 5000만

    @Autowired
    private GoalRecommendationService recommendationService;

    // ===== 케이스 1: 조건 최소 (regionCode 만) =====

    @Test
    @DisplayName("[조건 최소] 대출 없는 회원 — regionCode '11110' 만 지정, 나머지는 알고리즘이 채움")
    void 조건_최소_대출없는회원() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");

        GoalRecommendationResponse response = recommendationService.recommend(MEMBER_NO_LOAN, request);

        print("조건 최소 / member=" + MEMBER_NO_LOAN, response);
        assertThat(response.getRecommendations()).isNotEmpty();
    }

    @Test
    @DisplayName("[조건 최소] 대출 있는 회원 — 기존 대출 월상환액이 차감된 예산으로 추천")
    void 조건_최소_대출있는회원() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");

        GoalRecommendationResponse response = recommendationService.recommend(MEMBER_WITH_LOAN, request);

        print("조건 최소 / member=" + MEMBER_WITH_LOAN + " (대출 1억)", response);
        assertThat(response.getRecommendations()).isNotEmpty();
    }

    // ===== 케이스 2: 주거유형·거래유형·평수 지정 =====

    @Test
    @DisplayName("[조건 지정] 아파트·전세·15~25평 — 각 알고리즘이 해당 조건 기준으로 추천")
    void 조건_지정_APT_전세() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);
        request.setSizeMin(15);
        request.setSizeMax(25);

        GoalRecommendationResponse response = recommendationService.recommend(MEMBER_NO_LOAN, request);

        print("APT 전세 15~25평 / member=" + MEMBER_NO_LOAN, response);
        assertThat(response.getRecommendations()).isNotEmpty();
        // PREFERENCE는 입력 조건을 그대로 유지, REALISTIC·HOLDOUT은 조건을 완화할 수 있다
        response.getRecommendations().stream()
                .filter(item -> item.getType() == com.team.independence.goal.dto.AlgorithmType.PREFERENCE)
                .forEach(item ->
                        assertThat(item.getCondition().getHousingType()).isEqualTo(HousingType.APT));
    }

    @Test
    @DisplayName("[조건 지정] 오피스텔·월세 — 월세 조건 결과에 monthlyRent 포함 여부 확인")
    void 조건_지정_오피스텔_월세() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.OFFICETEL);
        request.setTradeType(DealType.WOLSE);
        request.setMonthlyRentMax(1_500_000L);

        GoalRecommendationResponse response = recommendationService.recommend(MEMBER_NO_LOAN, request);

        print("오피스텔 월세 / member=" + MEMBER_NO_LOAN, response);
        assertThat(response.getRecommendations()).isNotEmpty();
        response.getRecommendations().stream()
                .filter(item -> item.isFeasible())
                .forEach(item -> {
                    if (item.getCondition().getDealType() == DealType.WOLSE) {
                        assertThat(item.getCondition().getMonthlyRent()).isPositive();
                    }
                });
    }

    // ===== 케이스 3: 시도 코드로 광역 탐색 =====

    @Test
    @DisplayName("[시도 탐색] regionCode '11' — 서울 전체에서 최적 시군구를 알고리즘이 선정")
    void 시도코드_서울_광역탐색() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);

        GoalRecommendationResponse response = recommendationService.recommend(MEMBER_NO_LOAN, request);

        print("시도 탐색 / member=" + MEMBER_NO_LOAN, response);
        assertThat(response.getRecommendations()).isNotEmpty();
        // 결과 지역이 서울(11xxx) 안에 있어야 한다 (feasible=false 카드는 condition=null이므로 제외)
        response.getRecommendations().stream()
                .filter(item -> item.isFeasible())
                .forEach(item ->
                        assertThat(item.getCondition().getRegionCode()).startsWith("11"));
    }

    // ===== 케이스 4: 목표 시점 지정 =====

    @Test
    @DisplayName("[목표 시점] 24개월 후 지정 — loanX·loanO 플랜의 targetDate가 일치해야 함")
    void 목표시점_24개월후() {
        YearMonth targetDate = YearMonth.now().plusMonths(24);

        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setTargetDate(targetDate);

        GoalRecommendationResponse response = recommendationService.recommend(MEMBER_NO_LOAN, request);

        print("목표 시점 24개월 / member=" + MEMBER_NO_LOAN, response);
        assertThat(response.getRecommendations()).isNotEmpty();
        // PREFERENCE는 입력 targetDate를 그대로 사용, REALISTIC·HOLDOUT은 실제 도달 시점을 계산한다
        response.getRecommendations().stream()
                .filter(item -> item.getType() == com.team.independence.goal.dto.AlgorithmType.PREFERENCE)
                .forEach(item ->
                        assertThat(item.getLoanX().getTargetDate()).isEqualTo(targetDate));
        // 모든 알고리즘의 targetDate가 null이 아닌지만 검사
        response.getRecommendations().forEach(item ->
                assertThat(item.getLoanX().getTargetDate()).isNotNull());
    }

    // ===== 케이스 5: loanO 플랜 (소득 등록 회원) =====

    @Test
    @DisplayName("[대출 플랜] 소득 500만 회원 — loanO 플랜에 대출액·단축 개월수 포함 여부 확인")
    void 대출플랜_소득있는회원() {
        GoalRecommendationRequest request = new GoalRecommendationRequest();
        request.setRegionCode("11110");
        request.setPropertyType(HousingType.APT);
        request.setTradeType(DealType.JEONSE);

        GoalRecommendationResponse response = recommendationService.recommend(MEMBER_NO_LOAN, request);

        print("대출 플랜 / member=" + MEMBER_NO_LOAN + " (소득 500만)", response);
        assertThat(response.getRecommendations()).isNotEmpty();
        // 소득이 있으니 loanO가 최소 하나는 있어야 한다
        boolean anyLoanO = response.getRecommendations().stream()
                .anyMatch(item -> item.getLoanO() != null);
        assertThat(anyLoanO).as("소득 있는 회원은 loanO 플랜이 최소 하나 있어야 한다").isTrue();
    }

    // ===== 출력 헬퍼 =====

    private void print(String label, GoalRecommendationResponse response) {
        List<RecommendationItem> items = response.getRecommendations();
        System.out.println("\n========== " + label + " ==========");
        System.out.println("알고리즘 결과 수: " + items.size());

        for (RecommendationItem item : items) {
            System.out.println("\n  [" + item.getType() + "]");
            System.out.println("  title  : " + item.getTitle());
            System.out.println("  reason : " + item.getReason());

            if (item.getCondition() != null) {
                System.out.printf("  조건   : %s %s / %s / %d~%d평 / 표본 %d건%n",
                        item.getCondition().getRegionName(),
                        item.getCondition().getRegionCode(),
                        item.getCondition().getHousingType() + " " + item.getCondition().getDealType(),
                        item.getCondition().getAreaMin(),
                        item.getCondition().getAreaMax(),
                        item.getCondition().getSampleCount());
                if (item.getCondition().getMonthlyRent() > 0) {
                    System.out.printf("  월세   : %,d원%n", item.getCondition().getMonthlyRent());
                }
            }

            if (item.getLoanX() != null) {
                System.out.printf("  loanX  : 목표 %,d원 / %s / 월저축 %,d원%n",
                        item.getLoanX().getTargetAmount(),
                        item.getLoanX().getTargetDate(),
                        item.getLoanX().getMonthlySaving());
            }

            if (item.getLoanO() != null) {
                System.out.printf("  loanO  : 대출 %,d원 / 자기부담 %,d원 / 월저축 %,d원 / 단축 %s개월%n",
                        item.getLoanO().getLoanAmount(),
                        item.getLoanO().getTargetAmount(),
                        item.getLoanO().getMonthlySaving(),
                        item.getLoanO().getShortenedMonths() != null
                                ? item.getLoanO().getShortenedMonths() : "-");
            } else {
                System.out.println("  loanO  : 없음 (소득 미등록 또는 DSR 한도 소진)");
            }
        }
        System.out.println("==========================================");
    }
}
