package com.team.independence.property.service;

import com.team.independence.config.RootConfig;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class RentTransactionSyncServiceIntegrationTest {

    @Autowired
    private RentTransactionSyncService rentTransactionSyncService;

    @Autowired
    private DataSource dataSource;

    @Test
    void sync_저장확인() {
        rentTransactionSyncService.sync("11110", "201512");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM rent_transaction WHERE region_code = ? AND deal_ym = ?",
            Integer.class, "11110", "201512"
        );

        System.out.println("저장된 건수: " + count);
        assertTrue(count != null && count > 0, "rent_transaction에 저장된 데이터가 없습니다.");
    }
}
