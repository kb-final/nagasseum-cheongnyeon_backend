package com.team.independence.goal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.team.independence.config.RootConfig;
import com.team.independence.goal.domain.Goal;
import com.team.independence.goal.domain.GoalHousing;
import com.team.independence.goal.domain.SavingRecord;
import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 목표 상세 조회 매퍼의 XML 매핑 통합 테스트.
 *
 * <p>GoalDetailServiceImplTest는 가짜 매퍼를 끼워 자바 로직만 본다. 그래서 XML의
 * SQL과 resultMap은 한 줄도 검증되지 않는다. 이 테스트는 반대로 실제 MySQL에 붙어
 * 컬럼이 도메인 필드와 제대로 물리는지만 본다. 특히 컴파일로 잡히지 않는 것들 —
 * enum 변환(housing_type/deal_type), LocalDate, Boolean, 정렬과 LIMIT.
 *
 * <p><b>테스트가 만든 행은 끝나고 모두 지운다.</b> 다른 회원의 목표는 건드리지 않는다.
 *
 * <p>실제 DB가 필요해 CI에서는 돌릴 수 없다. 로컬에서 확인할 때만
 * 아래 @Disabled를 잠시 주석 처리하고 실행한다.
 *
 * <p>사전 조건: member와 region에 행이 하나라도 있어야 한다(FK). 없으면 건너뛴다.
 */
@Disabled("실제 DB가 필요해 로컬에서만 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class GoalDetailMapperIntegrationTest {

    private static final long TARGET_AMOUNT = 300_000_000L;
    private static final long MONTHLY_SAVING = 1_500_000L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2027, 12, 1);

    @Autowired
    private GoalMapper goalMapper;

    @Autowired
    private GoalHousingMapper goalHousingMapper;

    @Autowired
    private SavingRecordMapper savingRecordMapper;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    /** 테스트가 만든 목표. FK 때문에 자식 행부터 지워야 한다. */
    private Long goalId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);

        memberId = firstOrNull("SELECT id FROM member ORDER BY id LIMIT 1", Long.class);
        String regionCode = firstOrNull("SELECT code FROM region ORDER BY code LIMIT 1", String.class);
        assumeTrue(memberId != null && regionCode != null,
                "member 또는 region에 데이터가 없어 건너뜁니다. sql/full_schema.sql과 초기 데이터를 넣어주세요.");

        Goal goal = Goal.builder()
                .memberId(memberId)
                .goalType("HOUSING")
                .targetAmount(TARGET_AMOUNT)
                .targetRentMiddleAmount(280_000_000L)
                .targetDate(TARGET_DATE)
                .monthlySaving(MONTHLY_SAVING)
                .status("ACTIVE")
                .build();
        goalMapper.insert(goal);
        goalId = goal.getId();

        goalHousingMapper.insert(GoalHousing.builder()
                .goalId(goalId)
                .regionCode(regionCode)
                .housingType(HousingType.OFFICETEL)
                .dealType(DealType.WOLSE)
                .areaMin(15)
                .areaMax(25)
                .depositMin(50_000_000L)
                .depositMax(100_000_000L)
                .monthlyRentMin(300_000L)
                .monthlyRentMax(700_000L)
                .build());

        // 연월을 일부러 뒤섞어 넣어 정렬이 SQL에서 이뤄지는지 본다.
        insertSavingRecord("202605", 1_450_000L, false);
        insertSavingRecord("202607", 2_000_000L, true);
        insertSavingRecord("202604", 1_000_000L, false);
        insertSavingRecord("202606", 1_800_000L, false);
    }

    @AfterEach
    void tearDown() {
        if (goalId == null) {
            return;
        }
        jdbc.update("DELETE FROM saving_record WHERE goal_id = ?", goalId);
        jdbc.update("DELETE FROM goal_housing  WHERE goal_id = ?", goalId);
        jdbc.update("DELETE FROM goal          WHERE id      = ?", goalId);
    }

    // ------------------------------------------------------------------
    // goal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("목표의 모든 컬럼이 도메인 필드에 담긴다")
    void 목표_컬럼_매핑() {
        Goal found = goalMapper.findById(goalId);

        assertNotNull(found);
        assertEquals(goalId, found.getId());
        assertEquals(memberId, found.getMemberId());
        assertEquals("HOUSING", found.getGoalType());
        assertEquals(TARGET_AMOUNT, found.getTargetAmount().longValue());
        assertEquals(280_000_000L, found.getTargetRentMiddleAmount().longValue());
        assertEquals(TARGET_DATE, found.getTargetDate());
        assertEquals(MONTHLY_SAVING, found.getMonthlySaving().longValue());
        assertEquals("ACTIVE", found.getStatus());
        // 저장 시 DEFAULT가 채우는 값이라 조회로만 확인된다.
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    @DisplayName("시세 알림 관련 컬럼은 NULL 그대로 내려온다")
    void 목표_널_컬럼() {
        Goal found = goalMapper.findById(goalId);

        assertNull(found.getMarketAlertDismissedAt());
        assertNull(found.getMarketAlertDismissedPrice());
    }

    @Test
    @DisplayName("없는 목표를 찾으면 null")
    void 없는_목표는_널() {
        assertNull(goalMapper.findById(-1L));
    }

    // ------------------------------------------------------------------
    // goal_housing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("주거 조건의 문자열 컬럼이 enum으로 변환된다")
    void 주거조건_enum_매핑() {
        GoalHousing found = goalHousingMapper.findByGoalId(goalId);

        assertNotNull(found);
        assertEquals(HousingType.OFFICETEL, found.getHousingType());
        assertEquals(DealType.WOLSE, found.getDealType());
    }

    @Test
    @DisplayName("주거 조건의 범위 컬럼이 모두 담긴다")
    void 주거조건_범위_매핑() {
        GoalHousing found = goalHousingMapper.findByGoalId(goalId);

        assertEquals(15, found.getAreaMin().intValue());
        assertEquals(25, found.getAreaMax().intValue());
        assertEquals(50_000_000L, found.getDepositMin().longValue());
        assertEquals(100_000_000L, found.getDepositMax().longValue());
        assertEquals(300_000L, found.getMonthlyRentMin().longValue());
        assertEquals(700_000L, found.getMonthlyRentMax().longValue());
    }

    @Test
    @DisplayName("주거 조건이 없는 목표면 null")
    void 없는_주거조건은_널() {
        assertNull(goalHousingMapper.findByGoalId(-1L));
    }

    // ------------------------------------------------------------------
    // saving_record
    // ------------------------------------------------------------------

    @Test
    @DisplayName("저축 기록은 최근 연월부터 내려오고 LIMIT이 걸린다")
    void 저축기록_정렬과_제한() {
        List<SavingRecord> records = savingRecordMapper.findRecentByGoalId(goalId, 3);

        assertEquals(3, records.size());
        assertEquals("202607", records.get(0).getRecordYm());
        assertEquals("202606", records.get(1).getRecordYm());
        assertEquals("202605", records.get(2).getRecordYm());
    }

    @Test
    @DisplayName("저축 기록의 금액과 수정 여부가 담긴다")
    void 저축기록_컬럼_매핑() {
        SavingRecord latest = savingRecordMapper.findRecentByGoalId(goalId, 1).get(0);

        assertEquals(goalId, latest.getGoalId());
        assertEquals(2_000_000L, latest.getActualSaving().longValue());
        assertEquals(MONTHLY_SAVING, latest.getTargetSaving().longValue());
        assertTrue(latest.getIsModified());
        assertNotNull(latest.getCreatedAt());
    }

    @Test
    @DisplayName("기록이 LIMIT보다 적으면 있는 만큼만 내려온다")
    void 기록이_적으면_있는만큼() {
        jdbc.update("DELETE FROM saving_record WHERE goal_id = ? AND record_ym <> '202607'", goalId);

        List<SavingRecord> records = savingRecordMapper.findRecentByGoalId(goalId, 3);

        assertEquals(1, records.size());
        assertEquals("202607", records.get(0).getRecordYm());
    }

    @Test
    @DisplayName("기록이 없는 목표면 빈 목록")
    void 기록이_없으면_빈_목록() {
        assertTrue(savingRecordMapper.findRecentByGoalId(-1L, 3).isEmpty());
    }

    // ------------------------------------------------------------------
    // helper
    // ------------------------------------------------------------------

    private void insertSavingRecord(String recordYm, long actualSaving, boolean isModified) {
        jdbc.update("INSERT INTO saving_record (goal_id, record_ym, target_saving, actual_saving, is_modified)"
                + " VALUES (?, ?, ?, ?, ?)", goalId, recordYm, MONTHLY_SAVING, actualSaving, isModified);
    }

    private <T> T firstOrNull(String sql, Class<T> type) {
        List<T> found = jdbc.queryForList(sql, type);
        return found.isEmpty() ? null : found.get(0);
    }
}
