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
    // 거래 유형 병합
    // ------------------------------------------------------------------

    @Test
    @DisplayName("인원이 2명 이하인 거래 유형은 기타로 합친다")
    void 소수_거래유형은_기타로_병합된다() {
        mapper.cohortCount = 20;
        mapper.dealTypes = Arrays.asList(
                dealType("JEONSE", 16),
                dealType("WOLSE", 2),
                dealType("MAEMAE", 2));

        CompareResponse response = call();
        List<DealTypeItem> items = response.getDealTypeDistribution().getItems();

        // 월세 2명 + 매매 2명 → 기타 4명. 원래 유형은 사라져야 한다.
        assertEquals(2, items.size());
        assertEquals("JEONSE", items.get(0).getDealType());
        assertEquals("ETC", items.get(1).getDealType());
        assertEquals("기타", items.get(1).getLabel());
        assertEquals(20.0, items.get(1).getRatio(), 0.0001);
    }

    @Test
    @DisplayName("병합된 기타가 1위가 되면 순위도 다시 매긴다")
    void 기타가_가장_많으면_1위가_된다() {
        mapper.cohortCount = 10;
        mapper.dealTypes = Arrays.asList(
                dealType("JEONSE", 4),
                dealType("WOLSE", 2),
                dealType("MAEMAE", 2),
                dealType("BUNYANG", 2));

        CompareResponse response = call();
        List<DealTypeItem> items = response.getDealTypeDistribution().getItems();

        // 기타 6명 > 전세 4명. 쿼리 정렬만 믿으면 전세가 1위로 남는다.
        assertEquals("ETC", items.get(0).getDealType());
        assertEquals(1, items.get(0).getRank().intValue());
        assertEquals("ETC", response.getDealTypeDistribution().getTopDealType());
    }

    @Test
    @DisplayName("합칠 유형이 없으면 기타 항목을 만들지 않는다")
    void 병합대상이_없으면_기타는_없다() {
        mapper.cohortCount = 10;
        mapper.dealTypes = Arrays.asList(dealType("JEONSE", 7), dealType("WOLSE", 3));

        List<DealTypeItem> items = call().getDealTypeDistribution().getItems();

        assertEquals(2, items.size());
        assertTrue(items.stream().noneMatch(i -> "ETC".equals(i.getDealType())));
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
    @DisplayName("내 스냅샷이 없으면 집계 데이터 없음으로 처리한다")
    void 스냅샷이_없으면_예외() {
        mapper.me = null;

        assertEquals(ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND,
                assertThrows(BusinessException.class, this::call).getErrorCode());
    }

    @Test
    @DisplayName("집계된 달이 하나도 없어도 같은 예외로 처리한다")
    void 스냅샷_월이_없으면_예외() {
        mapper.latestYm = null;

        assertEquals(ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND,
                assertThrows(BusinessException.class, this::call).getErrorCode());
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
        public int countCohort(CohortCondition condition) {
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