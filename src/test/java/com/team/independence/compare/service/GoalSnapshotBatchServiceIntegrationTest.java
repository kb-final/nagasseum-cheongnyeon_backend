package com.team.independence.compare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.team.independence.config.RootConfig;

/**
 * 목표 스냅샷 배치 통합 테스트.
 *
 * <p>실제 MySQL이 필요해 CI에서는 돌릴 수 없다. 로컬에서 확인할 때만
 * 아래 @Disabled를 잠시 주석 처리하고 실행한다.
 *
 * <p>사전 조건
 * <ol>
 *   <li>docker compose up -d
 *   <li>sql/test_data.sql 실행 (202607 스냅샷 70건)
 *   <li>sql/test_batch_source.sql 실행 (goal_housing, asset_snapshot 202608 채우기)
 * </ol>
 *
 * <p>202608로 돌리는 이유는 지금까지 검증에 써온 202607 데이터를 건드리지 않기 위해서다.
 */
@Disabled("실제 DB가 필요해 로컬에서만 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class GoalSnapshotBatchServiceIntegrationTest {

    private static final String TEST_YM = "202608";

    @Autowired
    private GoalSnapshotBatchService goalSnapshotBatchService;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("원본 테이블에서 스냅샷이 만들어진다")
    void 스냅샷_생성() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM goal_snapshot WHERE snapshot_ym = ?", TEST_YM);

        int affected = goalSnapshotBatchService.generate(TEST_YM);

        Integer saved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM goal_snapshot WHERE snapshot_ym = ?", Integer.class, TEST_YM);

        assertTrue(affected > 0, "생성된 건수가 0입니다. goal_housing / asset_snapshot이 비어 있는지 확인하세요");
        assertEquals(affected, saved);
    }

    @Test
    @DisplayName("같은 달에 두 번 돌려도 건수가 늘지 않는다")
    void 재실행해도_안전하다() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        goalSnapshotBatchService.generate(TEST_YM);
        Integer first = jdbc.queryForObject(
                "SELECT COUNT(*) FROM goal_snapshot WHERE snapshot_ym = ?", Integer.class, TEST_YM);

        goalSnapshotBatchService.generate(TEST_YM);
        Integer second = jdbc.queryForObject(
                "SELECT COUNT(*) FROM goal_snapshot WHERE snapshot_ym = ?", Integer.class, TEST_YM);

        // ON DUPLICATE KEY UPDATE가 없으면 여기서 중복 키 예외가 나거나 건수가 두 배가 된다.
        assertEquals(first, second);
    }

    @Test
    @DisplayName("회원 한 명당 한 건만 만들어진다")
    void 회원당_한_건이다() {
        goalSnapshotBatchService.generate(TEST_YM);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer duplicated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "  SELECT member_id FROM goal_snapshot WHERE snapshot_ym = ?"
                        + "   GROUP BY member_id HAVING COUNT(*) > 1"
                        + ") t", Integer.class, TEST_YM);

        assertEquals(0, duplicated.intValue());
    }

    @Test
    @DisplayName("달성률은 0~100 안에 들어온다")
    void 달성률_범위() {
        goalSnapshotBatchService.generate(TEST_YM);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer outOfRange = jdbc.queryForObject(
                "SELECT COUNT(*) FROM goal_snapshot"
                        + " WHERE snapshot_ym = ? AND (achievement_rate < 0 OR achievement_rate > 100)",
                Integer.class, TEST_YM);

        assertEquals(0, outOfRange.intValue());
    }
}