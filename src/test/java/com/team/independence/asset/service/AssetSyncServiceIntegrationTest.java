package com.team.independence.asset.service;

import com.team.independence.asset.mapper.AssetAccountMapper;
import com.team.independence.asset.mapper.LoanAccountMapper;
import com.team.independence.asset.mapper.AssetSummaryMapper;
import com.team.independence.common.security.AesEncryptor;
import com.team.independence.config.RootConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자산 동기화 통합 테스트.
 *
 * CodefMockClient(@Profile("test"))가 자동으로 주입되므로 실제 CODEF API를 호출하지 않는다.
 * CodefTokenManager는 Redis에 사전 주입된 mock-token을 읽어 HTTP 호출을 건너뛴다.
 *
 * 사전 조건: docker compose up -d (MySQL + Redis)
 * 실행 방법: @Disabled 제거 후 로컬에서 실행
 */
@Disabled("로컬 MySQL + Redis 환경 전용")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@ActiveProfiles({"local", "test"})
class AssetSyncServiceIntegrationTest {

    private static final long TEST_MEMBER_ID = 99999L;
    private static final long TEST_CONNECTED_ACCOUNT_ID = 99999L;
    private static final long TEST_INSTITUTION_ID = 99999L;

    @Autowired AssetSyncService assetSyncService;
    @Autowired AssetAccountMapper assetAccountMapper;
    @Autowired LoanAccountMapper loanAccountMapper;
    @Autowired AssetSummaryMapper assetSummaryMapper;
    @Autowired AesEncryptor aesEncryptor;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();

        // CodefTokenManager가 Redis 캐시를 읽어 CODEF OAuth 호출을 건너뛰도록 주입
        redisTemplate.opsForValue().set("codef:oauth:token", "mock-token");

        // institution 마스터 데이터 (없으면 삽입, 있으면 유지)
        jdbcTemplate.update(
                "INSERT IGNORE INTO institution (code, name, institution_type, business_type, login_type, product_label) "
                        + "VALUES (?,?,?,?,?,?)",
                "088", "KB국민은행", "BANK", "BK", "1", "예적금 · 대출");

        // member FK를 만족하기 위한 테스트 회원
        jdbcTemplate.update(
                "INSERT INTO member (id, kakao_id, nickname, birth_date) VALUES (?,?,?,?)",
                TEST_MEMBER_ID, "test-kakao-99999", "테스터", "2000-01-01");

        String encryptedId = aesEncryptor.encrypt("test-connected-id");
        jdbcTemplate.update(
                "INSERT INTO connected_account (id, member_id, connected_id, birth_date, connected_status) VALUES (?,?,?,?,?)",
                TEST_CONNECTED_ACCOUNT_ID, TEST_MEMBER_ID, encryptedId, "000101", "ACTIVE");
        jdbcTemplate.update(
                "INSERT INTO connected_institution (id, connected_account_id, institution_code, status) VALUES (?,?,?,?)",
                TEST_INSTITUTION_ID, TEST_CONNECTED_ACCOUNT_ID, "088", "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        cleanup();
        redisTemplate.delete("codef:oauth:token");
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM asset_account WHERE connected_institution_id = ?", TEST_INSTITUTION_ID);
        jdbcTemplate.update("DELETE FROM loan_account  WHERE connected_institution_id = ?", TEST_INSTITUTION_ID);
        jdbcTemplate.update("DELETE FROM asset_summary WHERE member_id = ?", TEST_MEMBER_ID);
        jdbcTemplate.update("DELETE FROM connected_institution WHERE id = ?", TEST_INSTITUTION_ID);
        jdbcTemplate.update("DELETE FROM connected_account WHERE id = ?", TEST_CONNECTED_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", TEST_MEMBER_ID);
    }

    @Test
    @DisplayName("syncAccounts - 은행 더미 응답으로 asset_account 5건, loan_account 1건이 저장된다")
    void syncAccounts_savesAccountsToDb() {
        assetSyncService.syncAccounts(TEST_MEMBER_ID);

        int assetCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_account WHERE connected_institution_id = ?",
                Integer.class, TEST_INSTITUTION_ID);
        int loanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loan_account WHERE connected_institution_id = ?",
                Integer.class, TEST_INSTITUTION_ID);

        // 마이너스통장 제외: 입출금, 적금, 청약, 정기예금, 은행펀드 = 5건
        assertThat(assetCount).isEqualTo(5);
        assertThat(loanCount).isEqualTo(1);
    }

    @Test
    @DisplayName("syncAccounts - asset_summary가 upsert된다")
    void syncAccounts_updatesAssetSummary() {
        assetSyncService.syncAccounts(TEST_MEMBER_ID);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_summary WHERE member_id = ?",
                Integer.class, TEST_MEMBER_ID);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("재동기화 시 기존 계좌를 삭제하고 새 계좌로 교체된다")
    void syncAccounts_replacesStaleAccounts() {
        // 첫 번째 동기화
        assetSyncService.syncAccounts(TEST_MEMBER_ID);

        // 임의로 추가 계좌를 삽입
        jdbcTemplate.update(
                "INSERT INTO asset_account (connected_institution_id, account_type, asset_category, account_display, product_name, current_value, raw_response)"
                        + " VALUES (?, 'DEMAND', 'CASH', 'stale-account', 'stale', 999, '{}')",
                TEST_INSTITUTION_ID);
        jdbcTemplate.update(
                "INSERT INTO asset_account (connected_institution_id, account_type, asset_category, account_display, product_name, current_value, raw_response)"
                        + " VALUES (?, 'DEPOSIT', 'DEPOSIT_SAVINGS', 'stale-account-2', 'stale', 999, '{}')",
                TEST_INSTITUTION_ID);

        // 두 번째 동기화: stale 계좌가 삭제되고 더미 응답 계좌로 교체
        assetSyncService.syncAccounts(TEST_MEMBER_ID);

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_account WHERE connected_institution_id = ?",
                Integer.class, TEST_INSTITUTION_ID);
        assertThat(count).isEqualTo(5); // stale 2건 제거 후 원래 5건만 남아야 함
    }
}
