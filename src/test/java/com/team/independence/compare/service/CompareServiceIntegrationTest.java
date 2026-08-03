package com.team.independence.compare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.compare.dto.CompareResponse;
import com.team.independence.compare.dto.CompareResponse.AchievementBucket;
import com.team.independence.compare.dto.CompareResponse.DealTypeItem;
import com.team.independence.compare.dto.CompareResponse.PopularRegion;
import com.team.independence.config.RootConfig;

/**
 * 또래 비교 조회 통합 테스트.
 *
 * <p>CompareServiceImplTest는 FakeMapper를 끼워 자바 로직만 본다. 그래서 XML의 SQL은
 * 한 줄도 검증되지 않는다. 이 테스트는 반대로 실제 MySQL에 붙어 쿼리가 정말 도는지,
 * 컬럼 별칭이 DTO 필드와 맞물리는지를 본다.
 *
 * <p><b>이 테스트는 DB에 아무것도 쓰지 않는다. 조회만 한다.</b>
 * 동의 정보(member_agreement)는 member 도메인 소관이라 비교 도메인에서 만들거나 지우지 않고,
 * 현재 상태를 읽어 그에 맞는 검증만 골라서 수행한다.
 *
 * <p>실제 DB가 필요해 CI에서는 돌릴 수 없다. 로컬에서 확인할 때만
 * 아래 @Disabled를 잠시 주석 처리하고 실행한다.
 *
 * <p>사전 조건: docker compose up -d / sql/test_data.sql 실행 (202607 스냅샷)
 *
 * <p>동의한 회원이 하나도 없으면 조회 검증은 건너뛴다(실패가 아니라 '보류'로 표시된다).
 * 그 경우에도 '동의하지 않았으면 거절한다'는 검증은 그대로 수행된다.
 */
@Disabled("실제 DB가 필요해 로컬에서만 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class CompareServiceIntegrationTest {

    private static final Long ASSET_RANGE = 10_000_000L;
    private static final Integer AGE_RANGE = 2;

    /** 이 범위로 좁히면 코호트가 최소 인원 아래로 떨어질 수 있다 */
    private static final Long NARROW_ASSET_RANGE = 5_000_000L;
    private static final Integer NARROW_AGE_RANGE = 1;

    @Autowired
    private CompareService compareService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    /** COMPARE_DATA에 동의했고 스냅샷도 있는 회원. 없으면 null */
    private Long agreedMemberId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        agreedMemberId = findAgreedMemberWithSnapshot();
    }

    /**
     * 조회 검증에 쓸 회원을 고른다.
     *
     * <p>동의 데이터를 새로 만들지 않고 이미 있는 것만 찾는다. 코호트가 큰 순으로 골라야
     * 최소 인원에 걸리지 않고 집계 경로까지 들어간다.
     */
    private Long findAgreedMemberWithSnapshot() {
        List<Long> found = jdbc.queryForList(
                "SELECT g.member_id"
                        + "  FROM goal_snapshot g"
                        + "  JOIN member_agreement a"
                        + "    ON a.member_id = g.member_id"
                        + "   AND a.agreement_type = 'COMPARE_DATA'"
                        + "   AND a.is_agreed = TRUE"
                        + " WHERE g.snapshot_ym = (SELECT MAX(snapshot_ym) FROM goal_snapshot)"
                        + " ORDER BY ("
                        + "   SELECT COUNT(*) FROM goal_snapshot c"
                        + "    WHERE c.snapshot_ym = g.snapshot_ym"
                        + "      AND c.age        BETWEEN g.age - 2 AND g.age + 2"
                        + "      AND c.net_assets BETWEEN g.net_assets - 10000000"
                        + "                           AND g.net_assets + 10000000"
                        + " ) DESC"
                        + " LIMIT 1", Long.class);

        return found.isEmpty() ? null : found.get(0);
    }

    /** 조회 검증용 회원이 없으면 테스트를 건너뛴다. */
    private CompareResponse callOrSkip() {
        assumeTrue(agreedMemberId != null,
                "COMPARE_DATA에 동의한 회원이 없어 건너뜁니다. "
                        + "회원가입 또는 마이페이지에서 또래 비교 동의를 켠 뒤 다시 실행하세요.");
        return compareService.getComparison(agreedMemberId, ASSET_RANGE, AGE_RANGE);
    }

    // ------------------------------------------------------------------
    // 조회 전체 흐름
    // ------------------------------------------------------------------

    @Test
    @DisplayName("실제 DB에서 비교 결과를 만들어낸다")
    void 조회가_끝까지_동작한다() {
        CompareResponse response = callOrSkip();

        // 집계 기준월은 goal_snapshot의 MAX(snapshot_ym)에서 온다.
        assertNotNull(response.getSnapshotYm());
        assertEquals(6, response.getSnapshotYm().length(), "YYYYMM 형식이어야 한다");

        assertEquals(ASSET_RANGE, response.getCohort().getAssetRange());
        assertEquals(AGE_RANGE, response.getCohort().getAgeRange());
        assertNotNull(response.getCohort().getCohortSize());
    }

    @Test
    @DisplayName("코호트 인원 수가 직접 센 값과 같다")
    void 코호트_인원이_쿼리와_일치한다() {
        CompareResponse response = callOrSkip();

        Integer expected = jdbc.queryForObject(
                "SELECT COUNT(*)"
                        + "  FROM goal_snapshot c"
                        + "  JOIN goal_snapshot me"
                        + "    ON me.member_id = ? AND me.snapshot_ym = c.snapshot_ym"
                        + " WHERE c.snapshot_ym = ?"
                        + "   AND c.net_assets BETWEEN me.net_assets - ? AND me.net_assets + ?"
                        + "   AND c.age        BETWEEN me.age        - ? AND me.age        + ?",
                Integer.class,
                agreedMemberId, response.getSnapshotYm(),
                ASSET_RANGE, ASSET_RANGE, AGE_RANGE, AGE_RANGE);

        assertEquals(expected, response.getCohort().getCohortSize());
    }

    // ------------------------------------------------------------------
    // 거래 유형 분포
    // ------------------------------------------------------------------

    @Test
    @DisplayName("거래 유형 비율 합이 100에 가깝다")
    void 거래유형_비율이_온전하다() {
        CompareResponse response = callOrSkip();
        assumeTrue(response.getDealTypeDistribution() != null, "코호트 인원 미달이라 건너뜁니다");

        List<DealTypeItem> items = response.getDealTypeDistribution().getItems();
        assertTrue(items.size() > 0, "거래 유형이 하나도 없다");

        double sum = items.stream().mapToDouble(DealTypeItem::getRatio).sum();
        // 각 항목을 따로 반올림하므로 정확히 100은 아니다. 0.5%까지 허용한다.
        assertEquals(100.0, sum, 0.5);

        // 순위는 1부터 빠짐없이 이어지고, 비율은 내림차순이어야 한다.
        for (int i = 0; i < items.size(); i++) {
            assertEquals(i + 1, items.get(i).getRank().intValue());
            assertNotNull(items.get(i).getLabel(), "화면 표시명이 비었다");
            if (i > 0) {
                assertTrue(items.get(i - 1).getRatio() >= items.get(i).getRatio(),
                        "비율 내림차순이 깨졌다");
            }
        }

        assertEquals(items.get(0).getDealType(), response.getDealTypeDistribution().getTopDealType());
    }

    // ------------------------------------------------------------------
    // 달성률 분포
    // ------------------------------------------------------------------

    @Test
    @DisplayName("달성률 구간은 항상 9칸이고 내 구간에만 표시가 붙는다")
    void 달성률_구간이_9칸이다() {
        CompareResponse response = callOrSkip();
        assumeTrue(response.getAchievementDistribution() != null, "코호트 인원 미달이라 건너뜁니다");

        List<AchievementBucket> buckets = response.getAchievementDistribution().getBuckets();
        assertEquals(9, buckets.size());

        // 마지막 칸만 폭이 두 배(80~100)다.
        assertEquals(0, buckets.get(0).getRangeMin().intValue());
        assertEquals(80, buckets.get(8).getRangeMin().intValue());
        assertEquals(100, buckets.get(8).getRangeMax().intValue());

        assertEquals(1, buckets.stream().filter(AchievementBucket::getIsMine).count(),
                "내 구간 표시는 정확히 하나여야 한다");

        // 구간별 인원 합은 코호트 전체와 같아야 한다. 빠지거나 겹치는 구간이 있으면 어긋난다.
        int total = buckets.stream().mapToInt(AchievementBucket::getCount).sum();
        assertEquals(response.getCohort().getCohortSize().intValue(), total);
    }

    @Test
    @DisplayName("내 달성률이 표시된 구간 안에 들어 있다")
    void 내_구간이_내_달성률과_맞는다() {
        CompareResponse response = callOrSkip();
        assumeTrue(response.getAchievementDistribution() != null, "코호트 인원 미달이라 건너뜁니다");

        double myRate = response.getAchievementDistribution().getMyRate();
        AchievementBucket mine = response.getAchievementDistribution().getBuckets().stream()
                .filter(AchievementBucket::getIsMine)
                .findFirst()
                .orElseThrow(() -> new AssertionError("내 구간이 없다"));

        assertTrue(myRate >= mine.getRangeMin() && myRate <= mine.getRangeMax(),
                "내 달성률 " + myRate + "가 구간 "
                        + mine.getRangeMin() + "~" + mine.getRangeMax() + " 밖이다");
    }

    // ------------------------------------------------------------------
    // 인기 지역 · 저축 구간
    // ------------------------------------------------------------------

    @Test
    @DisplayName("인기 지역은 최대 3개이고 지역명이 조인된다")
    void 인기지역이_조인된다() {
        CompareResponse response = callOrSkip();
        assumeTrue(response.getPopularRegions() != null, "코호트 인원 미달이라 건너뜁니다");

        List<PopularRegion> regions = response.getPopularRegions();
        assertTrue(regions.size() <= 3);

        for (int i = 0; i < regions.size(); i++) {
            PopularRegion region = regions.get(i);
            assertEquals(i + 1, region.getRank().intValue());
            assertNotNull(region.getRegionCode());
            // region 테이블 조인이 실패하면 여기서 null이 잡힌다.
            assertNotNull(region.getRegionName(), "지역명 조인이 안 됐다");
        }
    }

    @Test
    @DisplayName("저축 구간과 평균값이 채워진다")
    void 저축구간과_평균이_나온다() {
        CompareResponse response = callOrSkip();
        assumeTrue(response.getSavingRange() != null, "코호트 인원 미달이라 건너뜁니다");

        assertNotNull(response.getAverageTargetAmount());
        assertNotNull(response.getAveragePrepMonths());
        assertNotNull(response.getAchievementDistribution().getCohortAverageRate());

        assertNotNull(response.getSavingRange().getMyMonthlySaving());
        Long min = response.getSavingRange().getCohortRangeMin();
        Long max = response.getSavingRange().getCohortRangeMax();
        assertNotNull(min);
        assertNotNull(max);
        assertTrue(min <= max, "저축 구간 하한이 상한보다 크다");
    }

    // ------------------------------------------------------------------
    // k-익명성
    // ------------------------------------------------------------------

    @Test
    @DisplayName("범위를 좁혀 인원이 모자라면 통계를 내리지 않는다")
    void 인원_미달이면_통계가_없다() {
        assumeTrue(agreedMemberId != null, "COMPARE_DATA에 동의한 회원이 없어 건너뜁니다");

        CompareResponse response = compareService.getComparison(
                agreedMemberId, NARROW_ASSET_RANGE, NARROW_AGE_RANGE);

        // 좁혀도 인원이 충분할 수 있다. 그때는 검증할 것이 없다.
        assumeTrue(Boolean.FALSE.equals(response.getCohort().getSufficient()),
                "범위를 좁혀도 인원이 충분해 건너뜁니다");

        assertNull(response.getDealTypeDistribution());
        assertNull(response.getAchievementDistribution());
        assertNull(response.getPopularRegions());
        assertNull(response.getSavingRange());
        assertNull(response.getAverageTargetAmount());
        assertNull(response.getAveragePrepMonths());
        assertNotNull(response.getCohort().getMinimumRequired());
    }

    // ------------------------------------------------------------------
    // 동의 · 범위 검사
    //
    // 아래 검증은 동의 데이터를 만들지 않고 '현재 상태'를 그대로 활용한다.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("동의하지 않은 회원은 거절한다")
    void 미동의면_거절한다() {
        List<Long> notAgreed = jdbc.queryForList(
                "SELECT g.member_id"
                        + "  FROM goal_snapshot g"
                        + " WHERE NOT EXISTS ("
                        + "   SELECT 1 FROM member_agreement a"
                        + "    WHERE a.member_id = g.member_id"
                        + "      AND a.agreement_type = 'COMPARE_DATA'"
                        + "      AND a.is_agreed = TRUE)"
                        + " LIMIT 1", Long.class);

        assumeTrue(!notAgreed.isEmpty(), "모든 회원이 동의한 상태라 건너뜁니다");

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> compareService.getComparison(notAgreed.get(0), ASSET_RANGE, AGE_RANGE));
        assertEquals(ErrorCode.COMPARE_CONSENT_REQUIRED, thrown.getErrorCode());
    }

    @Test
    @DisplayName("허용 범위를 벗어난 요청은 동의 여부와 무관하게 거절한다")
    void 범위를_벗어나면_거절한다() {
        // 범위 검사가 동의 검사보다 먼저라 어떤 회원 ID를 넣어도 같은 결과여야 한다.
        BusinessException assetThrown = assertThrows(BusinessException.class,
                () -> compareService.getComparison(1L, 999L, AGE_RANGE));
        assertEquals(ErrorCode.COMPARE_INVALID_RANGE, assetThrown.getErrorCode());

        BusinessException ageThrown = assertThrows(BusinessException.class,
                () -> compareService.getComparison(1L, ASSET_RANGE, 99));
        assertEquals(ErrorCode.COMPARE_INVALID_RANGE, ageThrown.getErrorCode());
    }

    @Test
    @DisplayName("스냅샷이 없는 회원은 집계 데이터 없음으로 처리한다")
    void 스냅샷이_없으면_거절한다() {
        List<Long> noSnapshot = jdbc.queryForList(
                "SELECT a.member_id"
                        + "  FROM member_agreement a"
                        + " WHERE a.agreement_type = 'COMPARE_DATA'"
                        + "   AND a.is_agreed = TRUE"
                        + "   AND NOT EXISTS ("
                        + "     SELECT 1 FROM goal_snapshot g WHERE g.member_id = a.member_id)"
                        + " LIMIT 1", Long.class);

        assumeTrue(!noSnapshot.isEmpty(), "동의했지만 스냅샷이 없는 회원이 없어 건너뜁니다");

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> compareService.getComparison(noSnapshot.get(0), ASSET_RANGE, AGE_RANGE));
        assertEquals(ErrorCode.COMPARE_SNAPSHOT_NOT_FOUND, thrown.getErrorCode());
    }
}