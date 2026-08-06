package com.team.independence.compare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.compare.domain.GoalSnapshot;
import com.team.independence.compare.dto.AchievementBucketCount;
import com.team.independence.compare.dto.CohortAverages;
import com.team.independence.compare.dto.CohortCondition;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.dto.CompareResponse.AchievementBucket;
import com.team.independence.compare.dto.CompareResponse.DealTypeItem;
import com.team.independence.compare.dto.DealTypeCount;
import com.team.independence.compare.dto.RegionCount;
import com.team.independence.compare.dto.SavingRangeResult;
import com.team.independence.compare.mapper.GoalSnapshotMapper;
import com.team.independence.member.domain.Agreement;
import com.team.independence.member.domain.Agreement.AgreementType;
import com.team.independence.member.service.AgreementService;

/**
 * 또래 비교 집계 로직 테스트.
 *
 * <p>DB 없이 돈다. Mapper 자리에 직접 만든 가짜 구현(FakeMapper)을 끼워 넣어
 * 쿼리 결과를 마음대로 정해주고, 서비스가 그걸로 무엇을 만드는지만 본다.
 * pom.xml에 Mockito가 없어 공용 파일을 건드리지 않으려고 이렇게 했다.
 */
class CompareServiceImplTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ASSET_RANGE = 10_000_000L;
    private static final Integer AGE_RANGE = 2;

    private FakeMapper mapper;
    private FakeAgreementService agreementService;
    private CompareServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = new FakeMapper();
        agreementService = new FakeAgreementService();
        service = new CompareServiceImpl(mapper, agreementService);
    }

    // ------------------------------------------------------------------
    // 거래 유형 분포
    // ------------------------------------------------------------------

    @Test
    @DisplayName("인원이 1명인 거래 유형도 합치지 않고 그대로 내려준다")
    void 소수_유형도_그대로_내려준다() {
        mapper.cohortCount = 20;
        mapper.dealTypes = Arrays.asList(
                dealType("JEONSE", 18),
                dealType("WOLSE", 1),
                dealType("MAEMAE", 1));

        List<DealTypeItem> items = call().getDealTypeDistribution().getItems();

        // 코호트 최소 인원을 이미 통과한 뒤라 유형이 1명이어도 그 사람이 특정되지 않는다.
        assertEquals(3, items.size());
        assertTrue(items.stream().noneMatch(i -> "ETC".equals(i.getDealType())));
        assertEquals("WOLSE", items.get(1).getDealType());
        assertEquals(2, items.get(1).getRank().intValue());
    }

    @Test
    @DisplayName("순위는 쿼리가 준 순서를 그대로 따른다")
    void 순위는_쿼리_순서를_따른다() {
        mapper.cohortCount = 10;
        mapper.dealTypes = Arrays.asList(dealType("JEONSE", 7), dealType("WOLSE", 3));

        List<DealTypeItem> items = call().getDealTypeDistribution().getItems();

        assertEquals("JEONSE", items.get(0).getDealType());
        assertEquals("전세", items.get(0).getLabel());
        assertEquals(1, items.get(0).getRank().intValue());
        assertEquals(70.0, items.get(0).getRatio(), 0.0001);
        assertEquals("JEONSE", call().getDealTypeDistribution().getTopDealType());
    }

    // ------------------------------------------------------------------
    // 달성률 분포
    // ------------------------------------------------------------------

    @Test
    @DisplayName("인원이 0인 구간도 자리를 지킨다")
    void 빈_구간도_9칸에_포함된다() {
        mapper.cohortCount = 10;
        // 쿼리는 인원이 있는 구간만 돌려준다. 0~10과 10~20은 아예 없다.
        mapper.buckets = Arrays.asList(bucket(2, 4), bucket(6, 6));

        List<AchievementBucket> buckets = call().getAchievementDistribution().getBuckets();

        assertEquals(9, buckets.size());
        assertEquals(0, buckets.get(0).getCount().intValue());
        assertEquals(0.0, buckets.get(0).getRatio(), 0.0001);
        assertEquals(4, buckets.get(2).getCount().intValue());
    }

    @Test
    @DisplayName("마지막 구간만 80~100으로 폭이 두 배다")
    void 마지막_구간은_80에서_100이다() {
        mapper.cohortCount = 10;
        List<AchievementBucket> buckets = call().getAchievementDistribution().getBuckets();

        assertEquals(0, buckets.get(0).getRangeMin().intValue());
        assertEquals(10, buckets.get(0).getRangeMax().intValue());
        assertEquals(80, buckets.get(8).getRangeMin().intValue());
        assertEquals(100, buckets.get(8).getRangeMax().intValue());
    }

    @Test
    @DisplayName("내 달성률이 속한 구간에만 isMine이 붙는다")
    void 내_구간에만_깃발이_꽂힌다() {
        mapper.cohortCount = 10;
        mapper.me.setAchievementRate(60.0);

        List<AchievementBucket> buckets = call().getAchievementDistribution().getBuckets();

        assertTrue(buckets.get(6).getIsMine());
        assertEquals(1, buckets.stream().filter(AchievementBucket::getIsMine).count());
    }

    @Test
    @DisplayName("달성률 80 이상은 모두 마지막 구간에 들어간다")
    void 달성률_100도_마지막_구간이다() {
        mapper.cohortCount = 10;
        mapper.me.setAchievementRate(100.0);

        List<AchievementBucket> buckets = call().getAchievementDistribution().getBuckets();

        // (int)(100 / 10) = 10. 방어하지 않으면 배열 밖을 가리킨다.
        assertTrue(buckets.get(8).getIsMine());
    }

    // ------------------------------------------------------------------
    // k-익명성
    // ------------------------------------------------------------------

    @Test
    @DisplayName("코호트가 최소 인원에 못 미치면 집계를 내리지 않는다")
    void 인원_미달이면_통계를_숨긴다() {
        mapper.cohortCount = 9;

        CompareResponse response = call();

        assertFalse(response.getCohort().getSufficient());
        assertEquals(10, response.getCohort().getMinimumRequired().intValue());
        assertEquals(9, response.getCohort().getCohortSize().intValue());

        assertNull(response.getDealTypeDistribution());
        assertNull(response.getAverageTargetAmount());
        assertNull(response.getAveragePrepMonths());
        assertNull(response.getAchievementDistribution());
        assertNull(response.getPopularRegions());
        assertNull(response.getSavingRange());
    }

    @Test
    @DisplayName("인원 미달이면 집계 쿼리를 아예 실행하지 않는다")
    void 인원_미달이면_쿼리를_돌리지_않는다() {
        mapper.cohortCount = 9;

        call();

        // 보여주지도 않을 통계를 DB에서 긁어올 이유가 없다.
        assertEquals(0, mapper.aggregateCalls);
    }

    @Test
    @DisplayName("최소 인원과 같으면 정상 집계한다")
    void 딱_10명이면_통계를_보여준다() {
        mapper.cohortCount = 10;

        CompareResponse response = call();

        assertNull(response.getCohort().getSufficient());
        assertTrue(mapper.aggregateCalls > 0);
    }

    // ------------------------------------------------------------------
    // 예외
    // ------------------------------------------------------------------

    @Test
    @DisplayName("허용 범위를 벗어난 비교 기준은 거부한다")
    void 범위를_벗어나면_예외() {
        assertEquals(ErrorCode.COMPARE_INVALID_RANGE,
                assertThrows(BusinessException.class,
                        () -> service.getComparison(MEMBER_ID, 999L, AGE_RANGE)).getErrorCode());

        assertEquals(ErrorCode.COMPARE_INVALID_RANGE,
                assertThrows(BusinessException.class,
                        () -> service.getComparison(MEMBER_ID, ASSET_RANGE, 99)).getErrorCode());

        assertEquals(ErrorCode.COMPARE_INVALID_RANGE,
                assertThrows(BusinessException.class,
                        () -> service.getComparison(MEMBER_ID, null, AGE_RANGE)).getErrorCode());
    }

    @Test
    @DisplayName("목표도 없고 스냅샷도 없으면 목표 미설정으로 처리한다")
    void 목표가_없으면_미설정_예외() {
        mapper.me = null;
        mapper.live = null;
        mapper.hasActiveGoal = false;

        assertEquals(ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND,
                assertThrows(BusinessException.class, this::call).getErrorCode());
    }

    @Test
    @DisplayName("집계된 달이 하나도 없어도 목표가 없으면 같은 예외다")
    void 스냅샷_월이_없으면_예외() {
        mapper.latestYm = null;
        mapper.me = null;
        mapper.live = null;
        mapper.hasActiveGoal = false;

        assertEquals(ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND,
                assertThrows(BusinessException.class, this::call).getErrorCode());
    }

    @Test
    @DisplayName("목표는 있는데 자산이 없으면 자산 연동 안내로 처리한다")
    void 자산이_없으면_연동_예외() {
        // 순자산이 코호트 범위의 기준이라, 0으로 채우면 엉뚱한 또래와 묶인다.
        mapper.me = null;
        mapper.live = null;
        mapper.hasActiveGoal = true;

        assertEquals(ErrorCode.COMPARE_ASSET_REQUIRED,
                assertThrows(BusinessException.class, this::call).getErrorCode());
    }

    @Test
    @DisplayName("기준값을 못 만들면 집계 쿼리는 돌리지 않는다")
    void 기준값이_없으면_쿼리를_돌리지_않는다() {
        mapper.me = null;
        mapper.live = null;
        mapper.hasActiveGoal = true;

        assertThrows(BusinessException.class, this::call);

        assertEquals(0, mapper.aggregateCalls);
    }

    // ------------------------------------------------------------------
    // 스냅샷이 아직 없을 때 (목표를 세운 그 달)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("스냅샷이 없으면 지금 값으로 비교한다")
    void 스냅샷이_없으면_실시간_값을_쓴다() {
        // 매월 1일 배치 전에는 내 행이 없다. 그렇다고 한 달을 기다리게 하지 않는다.
        mapper.me = null;
        mapper.live = liveSnapshot(31, 40_000_000L, 75.0, 1_200_000L);
        mapper.hasActiveGoal = true;
        mapper.cohortCount = 10;

        CompareResponse response = call();

        assertEquals(75.0, response.getAchievementDistribution().getMyRate(), 0.0001);
        assertEquals(1_200_000L, response.getSavingRange().getMyMonthlySaving().longValue());
    }

    @Test
    @DisplayName("스냅샷이 있으면 지금 값을 읽지 않는다")
    void 스냅샷이_있으면_실시간_조회를_하지_않는다() {
        // 이미 굳은 값이 있으면 그게 정답이다. 쓸데없이 한 번 더 읽지 않는다.
        mapper.cohortCount = 10;

        call();

        assertEquals(0, mapper.liveCalls);
    }

    @Test
    @DisplayName("실시간 값도 코호트 범위의 기준이 된다")
    void 실시간_값으로_코호트_범위를_잡는다() {
        mapper.me = null;
        mapper.live = liveSnapshot(31, 40_000_000L, 75.0, 1_200_000L);
        mapper.hasActiveGoal = true;
        mapper.cohortCount = 10;

        call();

        // ±1,000만 / ±2세가 실시간 값 기준으로 벌어져야 한다.
        assertEquals(30_000_000L, mapper.lastCondition.getNetAssetsMin());
        assertEquals(50_000_000L, mapper.lastCondition.getNetAssetsMax());
        assertEquals(29, mapper.lastCondition.getAgeMin());
        assertEquals(33, mapper.lastCondition.getAgeMax());
    }

    // ------------------------------------------------------------------
    // 약관 동의
    // ------------------------------------------------------------------

    @Test
    @DisplayName("비교 데이터 제공에 동의하지 않았으면 거절한다")
    void 미동의면_예외() {
        agreementService.compareAgreed = false;

        assertEquals(ErrorCode.COMPARE_CONSENT_REQUIRED,
                assertThrows(BusinessException.class, this::call).getErrorCode());
    }

    @Test
    @DisplayName("동의 기록이 아예 없으면 동의하지 않은 것으로 본다")
    void 동의기록이_없으면_예외() {
        // 개인정보라 '모르면 열어준다'가 아니라 '모르면 막는다'여야 한다.
        agreementService.agreements = new ArrayList<>();

        assertEquals(ErrorCode.COMPARE_CONSENT_REQUIRED,
                assertThrows(BusinessException.class, this::call).getErrorCode());
    }

    @Test
    @DisplayName("동의 검사는 범위 검사 다음에 한다")
    void 범위가_틀리면_동의보다_먼저_걸린다() {
        agreementService.compareAgreed = false;

        // 둘 다 잘못됐을 때 어느 쪽 코드가 나가는지 고정해둔다.
        assertEquals(ErrorCode.COMPARE_INVALID_RANGE,
                assertThrows(BusinessException.class,
                        () -> service.getComparison(MEMBER_ID, 999L, AGE_RANGE)).getErrorCode());
    }

    // ------------------------------------------------------------------
    // 비율 계산
    // ------------------------------------------------------------------

    @Test
    @DisplayName("비율은 소수 첫째 자리까지 반올림한다")
    void 비율은_소수_한자리다() {
        mapper.cohortCount = 34;
        mapper.dealTypes = Arrays.asList(dealType("JEONSE", 29), dealType("WOLSE", 5));

        List<DealTypeItem> items = call().getDealTypeDistribution().getItems();

        // 29/34 = 85.294... → 85.3
        assertEquals(85.3, items.get(0).getRatio(), 0.0001);
        assertEquals(14.7, items.get(1).getRatio(), 0.0001);
    }

    // ------------------------------------------------------------------
    // 도우미
    // ------------------------------------------------------------------

    private CompareResponse call() {
        return service.getComparison(MEMBER_ID, ASSET_RANGE, AGE_RANGE);
    }

    private static DealTypeCount dealType(String type, int count) {
        DealTypeCount row = new DealTypeCount();
        row.setDealType(type);
        row.setCount(count);
        return row;
    }

    private static AchievementBucketCount bucket(int index, int count) {
        AchievementBucketCount row = new AchievementBucketCount();
        row.setBucketIndex(index);
        row.setCount(count);
        return row;
    }

    /** 스냅샷이 없을 때 goal·asset_summary에서 즉석으로 만들어지는 기준값 */
    private static GoalSnapshot liveSnapshot(int age, long netAssets, double rate, long monthlySaving) {
        GoalSnapshot snapshot = new GoalSnapshot();
        snapshot.setMemberId(MEMBER_ID);
        snapshot.setAge(age);
        snapshot.setNetAssets(netAssets);
        snapshot.setAchievementRate(rate);
        snapshot.setMonthlySaving(monthlySaving);
        return snapshot;
    }

    /** 동의 여부를 테스트가 정해주는 가짜 AgreementService */
    private static class FakeAgreementService implements AgreementService {

        private boolean compareAgreed = true;
        private List<Agreement> agreements = null;

        @Override
        public List<Agreement> getAgreements(Long memberId) {
            if (agreements != null) {
                return agreements;
            }
            Agreement agreement = new Agreement();
            agreement.setMemberId(memberId);
            agreement.setAgreementType(AgreementType.COMPARE_DATA);
            agreement.setAgreed(compareAgreed);
            return Arrays.asList(agreement);
        }

        @Override
        public void saveAll(Long memberId, List<Agreement> agreements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAll(Long memberId, List<Agreement> agreements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateOne(Long memberId, AgreementType type, boolean agreed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByMemberId(Long memberId) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 쿼리 결과를 테스트가 직접 정해주는 가짜 Mapper.
     * aggregateCalls로 집계 쿼리가 실제로 불렸는지도 센다.
     */
    private static class FakeMapper implements GoalSnapshotMapper {

        private String latestYm = "202607";
        private GoalSnapshot me = defaultSnapshot();
        private int cohortCount = 10;
        private List<DealTypeCount> dealTypes = new ArrayList<>();
        private List<RegionCount> regions = new ArrayList<>();
        private List<AchievementBucketCount> buckets = new ArrayList<>();
        private int aggregateCalls = 0;

        /** 회원에게 진행 중인 목표가 있는지. 기준값을 못 만들 때 어떤 예외가 나갈지를 가른다 */
        private boolean hasActiveGoal = false;

        /** 스냅샷이 없을 때 대신 읽어오는 지금 값. null이면 자산 연동이 안 된 상태 */
        private GoalSnapshot live = null;
        private int liveCalls = 0;

        /** 집계 쿼리에 실제로 넘어간 코호트 조건. 범위를 제대로 잡았는지 확인용 */
        private CohortCondition lastCondition;

        private static GoalSnapshot defaultSnapshot() {
            GoalSnapshot snapshot = new GoalSnapshot();
            snapshot.setMemberId(MEMBER_ID);
            snapshot.setSnapshotYm("202607");
            snapshot.setAge(28);
            snapshot.setNetAssets(20_000_000L);
            snapshot.setAchievementRate(60.0);
            snapshot.setMonthlySaving(900_000L);
            return snapshot;
        }

        @Override
        public String findLatestSnapshotYm() {
            return latestYm;
        }

        @Override
        public GoalSnapshot findByMemberAndYm(Long memberId, String snapshotYm) {
            return me;
        }

        @Override
        public GoalSnapshot findLiveByMember(Long memberId, LocalDate baseDate) {
            liveCalls++;
            return live;
        }

        @Override
        public int countCohort(CohortCondition condition) {
            lastCondition = condition;
            return cohortCount;
        }

        @Override
        public List<DealTypeCount> countByDealType(CohortCondition condition) {
            aggregateCalls++;
            return dealTypes;
        }

        @Override
        public CohortAverages findAverages(CohortCondition condition) {
            aggregateCalls++;
            CohortAverages averages = new CohortAverages();
            averages.setAverageTargetAmount(24_852_941L);
            averages.setAveragePrepMonths(15);
            averages.setCohortAverageRate(49.7);
            return averages;
        }

        @Override
        public List<RegionCount> countTopRegions(CohortCondition condition) {
            aggregateCalls++;
            return regions;
        }

        @Override
        public List<AchievementBucketCount> countByAchievementBucket(CohortCondition condition) {
            aggregateCalls++;
            return buckets;
        }

        @Override
        public boolean existsActiveGoal(Long memberId) {
            return hasActiveGoal;
        }

        /**
         * 배치 전용 메서드라 이 테스트에서는 쓰지 않는다.
         * 실수로 조회 흐름에서 불리면 바로 드러나도록 예외를 던진다.
         */
        @Override
        public int insertSnapshots(String snapshotYm, LocalDate baseDate) {
            throw new UnsupportedOperationException("배치 전용 메서드입니다");
        }

        @Override
        public SavingRangeResult findSavingRange(CohortCondition condition) {
            aggregateCalls++;
            SavingRangeResult result = new SavingRangeResult();
            result.setCohortRangeMin(700_000L);
            result.setCohortRangeMax(900_000L);
            return result;
        }
    }
}