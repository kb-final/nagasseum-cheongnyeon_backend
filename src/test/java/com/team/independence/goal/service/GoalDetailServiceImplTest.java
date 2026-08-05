package com.team.independence.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team.independence.asset.dto.AssetLinkRequest;
import com.team.independence.asset.dto.AssetLinkResponse;
import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.LinkedOrganizationResponse;
import com.team.independence.asset.dto.UnlinkOrganizationResponse;
import com.team.independence.asset.service.AssetService;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.domain.SavingBasis;
import com.team.independence.goal.domain.SavingRecord;
import com.team.independence.goal.dto.GoalDetailResponse;
import com.team.independence.goal.dto.GoalForecastResponse;
import com.team.independence.goal.mapper.GoalHousingMapper;
import com.team.independence.goal.mapper.GoalMapper;
import com.team.independence.goal.mapper.SavingRecordMapper;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 목표 상세 조회 조립 로직 테스트.
 *
 * <p>DB 없이 돈다. Mapper와 AssetService 자리에 직접 만든 가짜 구현을 끼워 넣어
 * 조회 결과를 마음대로 정해주고, 서비스가 그걸로 무엇을 만드는지만 본다.
 * pom.xml에 Mockito가 없어 공용 파일을 건드리지 않으려고 이렇게 했다.
 *
 * <p>단 GoalService만은 진짜 구현을 쓴다. 예상 달성 시점 계산을 가짜로 바꾸면
 * forecasts 검증이 의미를 잃기 때문이다.
 */
class GoalDetailServiceImplTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long GOAL_ID = 100L;
    private static final Long FIXED_SAVING = 1_500_000L;

    private FakeGoalMapper goalMapper;
    private FakeGoalHousingMapper goalHousingMapper;
    private FakeSavingRecordMapper savingRecordMapper;
    private FakeAssetService assetService;
    private GoalDetailServiceImpl service;

    @BeforeEach
    void setUp() {
        goalMapper = new FakeGoalMapper();
        goalHousingMapper = new FakeGoalHousingMapper();
        savingRecordMapper = new FakeSavingRecordMapper();
        assetService = new FakeAssetService();
        // 소유권 검증과 도달 개월수 계산은 진짜 GoalServiceImpl이 담당한다.
        // 계산을 가짜로 바꾸면 forecasts 검증이 의미를 잃기 때문이다.
        // 그 외 의존성은 이 경로에서 쓰이지 않아 null로 둔다.
        service = new GoalDetailServiceImpl(
                goalHousingMapper, savingRecordMapper, assetService,
                new GoalServiceImpl(null, null, null, null, goalMapper, null, null));
    }

    // ------------------------------------------------------------------
    // 소유권
    // ------------------------------------------------------------------

    @Test
    @DisplayName("목표가 없으면 GOAL_NOT_FOUND")
    void 목표가_없으면_404() {
        goalMapper.goal = null;

        BusinessException e = assertThrows(BusinessException.class, this::call);
        assertEquals(ErrorCode.GOAL_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("다른 회원의 목표면 GOAL_FORBIDDEN")
    void 남의_목표면_403() {
        goalMapper.goal.setMemberId(999L);

        BusinessException e = assertThrows(BusinessException.class, this::call);
        assertEquals(ErrorCode.GOAL_FORBIDDEN, e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 목표 정보
    // ------------------------------------------------------------------

    @Test
    @DisplayName("목표 시점은 연월만 내려간다")
    void 목표시점은_연월() {
        goalMapper.goal.setTargetDate(LocalDate.of(2027, 12, 1));

        assertEquals(YearMonth.of(2027, 12), call().getTargetDate());
    }

    @Test
    @DisplayName("주거 조건은 goal_housing을 그대로 옮긴다")
    void 주거조건을_그대로_옮긴다() {
        GoalDetailResponse.Housing housing = call().getHousing();

        assertEquals("11680", housing.getRegionCode());
        assertEquals(HousingType.APT, housing.getHousingType());
        assertEquals(DealType.JEONSE, housing.getDealType());
        assertEquals(20, housing.getAreaMin().intValue());
        assertEquals(30, housing.getAreaMax().intValue());
    }

    // ------------------------------------------------------------------
    // 달성 현황
    // ------------------------------------------------------------------

    @Test
    @DisplayName("현재 자금은 이자 성장 자산과 원금 인정 자산의 합")
    void 현재자금은_순자산_합계() {
        assetService.growingAssets = 30_000_000L;
        assetService.fixedAssets = 20_000_000L;

        assertEquals(50_000_000L, call().getProgress().getCurrentAmount().longValue());
    }

    @Test
    @DisplayName("남은 금액과 달성률을 목표 금액 기준으로 계산한다")
    void 남은금액과_달성률() {
        goalMapper.goal.setTargetAmount(200_000_000L);
        assetService.growingAssets = 50_000_000L;
        assetService.fixedAssets = 0L;

        GoalDetailResponse.Progress progress = call().getProgress();

        assertEquals(150_000_000L, progress.getRemainingAmount().longValue());
        assertEquals(25.0, progress.getAchievementRate(), 0.0001);
    }

    @Test
    @DisplayName("목표를 넘겨도 남은 금액은 0, 달성률은 100을 넘지 않는다")
    void 초과달성해도_100을_넘지_않는다() {
        goalMapper.goal.setTargetAmount(100_000_000L);
        assetService.growingAssets = 300_000_000L;
        assetService.fixedAssets = 0L;

        GoalDetailResponse.Progress progress = call().getProgress();

        assertEquals(0L, progress.getRemainingAmount().longValue());
        assertEquals(100.0, progress.getAchievementRate(), 0.0001);
    }

    @Test
    @DisplayName("순자산이 마이너스여도 달성률은 0 밑으로 내려가지 않는다")
    void 순자산이_마이너스여도_0() {
        goalMapper.goal.setTargetAmount(100_000_000L);
        assetService.growingAssets = 0L;
        assetService.fixedAssets = -5_000_000L; // 대출이 자산보다 많은 경우

        assertEquals(0.0, call().getProgress().getAchievementRate(), 0.0001);
    }

    @Test
    @DisplayName("달성률은 소수 둘째 자리까지만 내려간다")
    void 달성률은_소수_둘째자리() {
        goalMapper.goal.setTargetAmount(300_000_000L);
        assetService.growingAssets = 100_000_000L;
        assetService.fixedAssets = 0L;

        // 33.3333...% -> 33.33
        assertEquals(33.33, call().getProgress().getAchievementRate(), 0.0001);
    }

    // ------------------------------------------------------------------
    // 저축 현황 (명세의 저축 기록 건수별 분기)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("저축 기록이 없으면 최근 저축액은 둘 다 null이고 forecasts는 FIXED뿐")
    void 기록_0건() {
        savingRecordMapper.records = Collections.emptyList();

        GoalDetailResponse response = call();

        assertEquals(FIXED_SAVING, response.getSavingStatus().getFixedSaving());
        assertNull(response.getSavingStatus().getLatestSaving());
        assertNull(response.getSavingStatus().getRecentAverageSaving());
        assertEquals(1, response.getForecasts().size());
        assertEquals(SavingBasis.FIXED, response.getForecasts().get(0).getBasis());
    }

    @Test
    @DisplayName("기록이 3건 미만이면 평균은 내지 않고 RECENT_AVERAGE도 빠진다")
    void 기록_2건() {
        savingRecordMapper.records = Arrays.asList(record("202607", 2_000_000L), record("202606", 1_800_000L));

        GoalDetailResponse response = call();

        assertEquals(2_000_000L, response.getSavingStatus().getLatestSaving().longValue());
        assertNull(response.getSavingStatus().getRecentAverageSaving());
        assertFalse(hasBasis(response, SavingBasis.RECENT_AVERAGE));
        assertTrue(hasBasis(response, SavingBasis.LATEST));
    }

    @Test
    @DisplayName("기록이 3건이면 평균을 내고 forecasts도 세 개가 된다")
    void 기록_3건() {
        savingRecordMapper.records = Arrays.asList(
                record("202607", 2_000_000L),
                record("202606", 1_800_000L),
                record("202605", 1_450_000L));

        GoalDetailResponse response = call();

        assertEquals(2_000_000L, response.getSavingStatus().getLatestSaving().longValue());
        assertEquals(1_750_000L, response.getSavingStatus().getRecentAverageSaving().longValue());
        assertEquals(3, response.getForecasts().size());
    }

    @Test
    @DisplayName("가장 최근 달은 조회 결과의 첫 번째 행이다")
    void 최근달은_첫번째_행() {
        savingRecordMapper.records = Arrays.asList(record("202607", 3_000_000L), record("202606", 1_000_000L));

        assertEquals(3_000_000L, call().getSavingStatus().getLatestSaving().longValue());
    }

    // ------------------------------------------------------------------
    // 예상 달성 시점
    // ------------------------------------------------------------------

    @Test
    @DisplayName("forecasts는 FIXED, RECENT_AVERAGE, LATEST 순서로 내려간다")
    void forecasts_순서() {
        savingRecordMapper.records = threeRecords();

        List<GoalForecastResponse> forecasts = call().getForecasts();

        assertEquals(SavingBasis.FIXED, forecasts.get(0).getBasis());
        assertEquals(SavingBasis.RECENT_AVERAGE, forecasts.get(1).getBasis());
        assertEquals(SavingBasis.LATEST, forecasts.get(2).getBasis());
    }

    @Test
    @DisplayName("고정 기준의 monthsDiff는 항상 0")
    void 고정기준의_차이는_0() {
        savingRecordMapper.records = threeRecords();

        assertEquals(0, findBasis(call(), SavingBasis.FIXED).getMonthsDiff().intValue());
    }

    @Test
    @DisplayName("더 많이 저축하면 예상 시점이 앞당겨지고 monthsDiff가 양수가 된다")
    void 더_저축하면_앞당겨진다() {
        savingRecordMapper.records = threeRecords(); // 평균 175만 > 고정 150만

        GoalDetailResponse response = call();
        GoalForecastResponse fixed = findBasis(response, SavingBasis.FIXED);
        GoalForecastResponse average = findBasis(response, SavingBasis.RECENT_AVERAGE);

        assertTrue(average.getMonthsDiff() > 0, "앞당겨졌으면 양수여야 한다: " + average.getMonthsDiff());
        assertTrue(average.getExpectedDate().isBefore(fixed.getExpectedDate()));
    }

    @Test
    @DisplayName("덜 저축하면 monthsDiff가 음수가 된다")
    void 덜_저축하면_지연된다() {
        savingRecordMapper.records = Arrays.asList(
                record("202607", 500_000L),
                record("202606", 500_000L),
                record("202605", 500_000L));

        assertTrue(findBasis(call(), SavingBasis.RECENT_AVERAGE).getMonthsDiff() < 0);
    }

    @Test
    @DisplayName("이미 달성했으면 예상 시점은 없고 차이는 0")
    void 이미_달성했으면_시점이_없다() {
        goalMapper.goal.setTargetAmount(100_000_000L);
        assetService.growingAssets = 200_000_000L;
        assetService.fixedAssets = 0L;
        savingRecordMapper.records = threeRecords();

        for (GoalForecastResponse forecast : call().getForecasts()) {
            assertNull(forecast.getExpectedDate(), forecast.getBasis() + "은 시점이 없어야 한다");
            assertEquals(0, forecast.getMonthsDiff().intValue());
        }
    }

    @Test
    @DisplayName("고정 저축액이 0이면 FIXED가 빠지고 나머지는 비교 기준이 없어 monthsDiff가 null")
    void 고정저축액이_0이면_비교불가() {
        goalMapper.goal.setMonthlySaving(0L);
        savingRecordMapper.records = threeRecords();

        GoalDetailResponse response = call();

        assertFalse(hasBasis(response, SavingBasis.FIXED));
        assertEquals(2, response.getForecasts().size());
        for (GoalForecastResponse forecast : response.getForecasts()) {
            assertNotNull(forecast.getExpectedDate());
            assertNull(forecast.getMonthsDiff(), forecast.getBasis() + "은 비교 기준이 없다");
        }
    }

    @Test
    @DisplayName("실제 저축액이 0인 달만 있으면 그 기준은 목록에서 빠진다")
    void 저축액_0인_기준은_제외() {
        savingRecordMapper.records = Arrays.asList(
                record("202607", 0L), record("202606", 0L), record("202605", 0L));

        GoalDetailResponse response = call();

        assertTrue(hasBasis(response, SavingBasis.FIXED));
        assertFalse(hasBasis(response, SavingBasis.RECENT_AVERAGE));
        assertFalse(hasBasis(response, SavingBasis.LATEST));
    }

    @Test
    @DisplayName("예상 시점은 이번 달을 기준으로 앞으로 간다")
    void 예상시점은_이번달_기준() {
        savingRecordMapper.records = Collections.emptyList();

        YearMonth expected = findBasis(call(), SavingBasis.FIXED).getExpectedDate();

        assertTrue(expected.isAfter(YearMonth.now()), "미래여야 한다: " + expected);
    }

    // ------------------------------------------------------------------
    // 조회 횟수
    // ------------------------------------------------------------------

    @Test
    @DisplayName("자산과 저축 기록은 각각 한 번씩만 조회한다")
    void 조회는_한_번씩만() {
        savingRecordMapper.records = threeRecords();

        call();

        assertEquals(1, assetService.netWorthCalls, "forecasts 세 개를 만들어도 자산 조회는 1회");
        assertEquals(1, savingRecordMapper.calls);
    }

    // ------------------------------------------------------------------
    // helper
    // ------------------------------------------------------------------

    private GoalDetailResponse call() {
        return service.getGoalDetail(MEMBER_ID, GOAL_ID);
    }

    private boolean hasBasis(GoalDetailResponse response, SavingBasis basis) {
        return response.getForecasts().stream().anyMatch(f -> f.getBasis() == basis);
    }

    private GoalForecastResponse findBasis(GoalDetailResponse response, SavingBasis basis) {
        return response.getForecasts().stream()
                .filter(f -> f.getBasis() == basis)
                .findFirst()
                .orElseThrow(() -> new AssertionError(basis + " 기준이 없다"));
    }

    /** 평균 175만원이 되는 3개월치 기록 */
    private List<SavingRecord> threeRecords() {
        return Arrays.asList(
                record("202607", 2_000_000L),
                record("202606", 1_800_000L),
                record("202605", 1_450_000L));
    }

    private SavingRecord record(String recordYm, long actualSaving) {
        return SavingRecord.builder()
                .goalId(GOAL_ID)
                .recordYm(recordYm)
                .targetSaving(FIXED_SAVING)
                .actualSaving(actualSaving)
                .isModified(false)
                .build();
    }

    // ------------------------------------------------------------------
    // fakes
    // ------------------------------------------------------------------

    private static class FakeGoalMapper implements GoalMapper {

        private Goal goal = defaultGoal();

        private static Goal defaultGoal() {
            return Goal.builder()
                    .id(GOAL_ID)
                    .memberId(MEMBER_ID)
                    .goalType("HOUSING")
                    .targetAmount(300_000_000L)
                    .targetRentMiddleAmount(280_000_000L)
                    .targetDate(LocalDate.of(2027, 12, 1))
                    .monthlySaving(FIXED_SAVING)
                    .status("ACTIVE")
                    .build();
        }

        @Override
        public Goal findById(Long id) {
            return goal;
        }

        @Override
        public void insert(Goal goal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsActiveByMemberId(Long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Goal findActiveByMemberId(Long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findAllActiveGoalIds() {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeGoalHousingMapper implements GoalHousingMapper {

        private GoalHousing housing = GoalHousing.builder()
                .goalId(GOAL_ID)
                .regionCode("11680")
                .housingType(HousingType.APT)
                .dealType(DealType.JEONSE)
                .areaMin(20)
                .areaMax(30)
                .depositMin(200_000_000L)
                .depositMax(300_000_000L)
                .monthlyRentMin(0L)
                .monthlyRentMax(0L)
                .build();

        @Override
        public GoalHousing findByGoalId(Long goalId) {
            return housing;
        }

        @Override
        public void insert(GoalHousing goalHousing) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeSavingRecordMapper implements SavingRecordMapper {

        private List<SavingRecord> records = new ArrayList<>();
        private int calls = 0;

        @Override
        public List<SavingRecord> findRecentByGoalId(Long goalId, int limit) {
            calls++;
            return records.size() > limit ? records.subList(0, limit) : records;
        }
    }

    private static class FakeAssetService implements AssetService {

        private long growingAssets = 100_000_000L;
        private long fixedAssets = 20_000_000L;
        private int netWorthCalls = 0;

        @Override
        public AssetNetWorthBreakdown getNetWorthBreakdown(Long memberId) {
            netWorthCalls++;
            return AssetNetWorthBreakdown.builder()
                    .interestBearingAssets(growingAssets)
                    .flatRecognizedAssets(fixedAssets)
                    .build();
        }

        @Override
        public void validateConnectedAccountExists(Long memberId) {
            // 연동돼 있다고 본다.
        }

        @Override
        public AssetLinkResponse linkAccount(Long memberId, AssetLinkRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LinkedOrganizationResponse> getConnections(Long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UnlinkOrganizationResponse unlinkOrganization(Long memberId, String organizationCode) {
            throw new UnsupportedOperationException();
        }
    }
}
