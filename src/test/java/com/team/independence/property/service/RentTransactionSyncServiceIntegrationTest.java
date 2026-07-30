package com.team.independence.property.service;

import com.team.independence.config.RootConfig;
import com.team.independence.property.domain.HousingType;
import javax.sql.DataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 MySQL과 국토부 API를 호출하는 통합 테스트라 CI에서는 실행할 수 없다.
 * 로컬에서 확인할 때만 이 @Disabled를 잠시 주석 처리하고 돌린다.
 *
 * <p>사전 조건: docker compose up -d, 환경변수 MOLIT_SERVICE_KEY
 */
@Disabled("실제 DB와 국토부 API가 필요해 로컬에서만 실행")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class RentTransactionSyncServiceIntegrationTest {

    @Autowired
    private RentTransactionSyncService rentTransactionSyncService;

    @Autowired
    private DataSource dataSource;

    @Test
    void collectAndSync_저장확인() {
        rentTransactionSyncService.collectAndSync("11110", "201512", HousingType.APT);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM rent_transaction WHERE region_code = ? AND deal_ym = ? AND housing_type = ?",
            Integer.class, "11110", "201512", "APT"
        );

        System.out.println("저장된 건수: " + count);
        assertTrue(count != null && count > 0, "rent_transaction에 저장된 데이터가 없습니다.");
    }

    /** 재적재이므로 같은 유닛을 두 번 돌려도 건수가 늘지 않아야 한다. */
    @Test
    void collectAndSync_두_번_실행해도_건수_동일() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        rentTransactionSyncService.collectAndSync("11110", "201512", HousingType.APT);
        Integer first = jdbc.queryForObject(
            "SELECT COUNT(*) FROM rent_transaction WHERE region_code = ? AND deal_ym = ? AND housing_type = ?",
            Integer.class, "11110", "201512", "APT"
        );

        rentTransactionSyncService.collectAndSync("11110", "201512", HousingType.APT);
        Integer second = jdbc.queryForObject(
            "SELECT COUNT(*) FROM rent_transaction WHERE region_code = ? AND deal_ym = ? AND housing_type = ?",
            Integer.class, "11110", "201512", "APT"
        );

        assertEquals(first, second, "재적재 후 건수가 달라졌습니다.");
    }
}
