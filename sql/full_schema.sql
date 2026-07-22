-- =====================================================================
-- 독립 준비 플랫폼 - 전체 DDL
-- MySQL 8.0 / utf8mb4 / 금액은 BIGINT(원 단위)
-- 생성 순서: 참조되는(부모) 테이블 → 참조하는(자식) 테이블
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- =====================================================================
-- [기준 테이블] 다른 테이블이 참조하므로 가장 먼저 생성
-- =====================================================================

-- 회원 -----------------------------------------------------------------
CREATE TABLE member (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    kakao_id              VARCHAR(50) NOT NULL COMMENT '카카오 고유 회원번호(로그인 키)',
    nickname              VARCHAR(20) NOT NULL COMMENT '표시 이름',
    birth_date            DATE        NULL     COMMENT '생년월일(연령 계산). 암호화 정책은 팀 결정',
    income_bracket        VARCHAR(20) NULL     COMMENT '소득 분위(드롭다운 선택, 예: INCOME_100_120)',
    income_bracket_updated_at DATETIME NULL    COMMENT '소득 분위 마지막 수정일',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_kakao (kakao_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회원';

-- 지역 코드 마스터 ------------------------------------------------------
CREATE TABLE region (
    code       VARCHAR(5)  NOT NULL COMMENT '법정동코드 앞 5자리(sggCd)',
    sido       VARCHAR(20) NOT NULL COMMENT '시도명',
    sigungu    VARCHAR(30) NOT NULL COMMENT '시군구명',
    full_name  VARCHAR(50) NOT NULL COMMENT '전체 지명',
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='지역 코드 매핑(시군구)';

-- 정책 -----------------------------------------------------------------
CREATE TABLE policies (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    source_api            VARCHAR(50)  NOT NULL COMMENT 'API 출처(YOUTH/GOV24)',
    policy_api_id         VARCHAR(100) NOT NULL COMMENT '원본 API 고유 ID',
    large_category        VARCHAR(50)  NULL     COMMENT '정책 대분류',
    medium_category       VARCHAR(50)  NULL     COMMENT '정책 중분류',
    providing_method      VARCHAR(50)  NULL     COMMENT '지원 방식(보조금/바우처 등)',
    policy_name           VARCHAR(200) NOT NULL COMMENT '정책명',
    policy_summary        TEXT         NULL     COMMENT '정책 요약',
    target_description    TEXT         NULL     COMMENT '지원 대상 설명',
    benefit_description   TEXT         NULL     COMMENT '혜택 내용',
    providing_org_name    VARCHAR(200) NULL     COMMENT '주관 기관명',
    apply_period_type     VARCHAR(50)  NOT NULL COMMENT '신청 기간 구분(상시/특정/마감)',
    apply_start_date      DATE         NULL     COMMENT '신청 시작일',
    apply_end_date        DATE         NULL     COMMENT '신청 종료일',
    apply_url             VARCHAR(500) NULL     COMMENT '온라인 신청 URL',
    min_age               TINYINT      NULL     COMMENT '신청 가능 최소 나이',
    max_age               TINYINT      NULL     COMMENT '신청 가능 최대 나이',
    extra                 JSON         NULL     COMMENT '원본 부가 필드',
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '현행 정책 여부',
    applicable_goal_types VARCHAR(100) NOT NULL DEFAULT 'HOUSING' COMMENT '목표유형 필터(HOUSING 등, 콤마구분)',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_policies_source (source_api, policy_api_id),
    KEY idx_policies_active (is_active),
    KEY idx_policies_age (min_age, max_age)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='정책';

-- 실거래 전월세 (4종 통합) ---------------------------------------------
CREATE TABLE rent_transaction (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    region_code    VARCHAR(5)    NOT NULL COMMENT '지역코드(sggCd) → region.code',
    housing_type   VARCHAR(20)   NOT NULL COMMENT 'APT/ROW_HOUSE/OFFICETEL/DETACHED',
    dong_name      VARCHAR(50)   NOT NULL COMMENT '법정동',
    jibun          VARCHAR(30)   NULL     COMMENT '지번(단독다가구 NULL)',
    complex_name   VARCHAR(100)  NULL     COMMENT '단지명(단독다가구 NULL)',
    area           DECIMAL(10,2) NULL     COMMENT '전용/연면적',
    deal_type      VARCHAR(10)   NOT NULL COMMENT 'JEONSE(전세)/WOLSE(월세)',
    deposit        BIGINT        NOT NULL DEFAULT 0 COMMENT '보증금(원)',
    monthly_rent   BIGINT        NOT NULL DEFAULT 0 COMMENT '월세(원, 전세=0)',
    floor          INT           NULL     COMMENT '층(단독다가구 NULL)',
    build_year     INT           NULL     COMMENT '건축년도',
    deal_ym        VARCHAR(6)    NOT NULL COMMENT '계약년월 YYYYMM',
    deal_day       INT           NULL     COMMENT '계약일',
    contract_type  VARCHAR(20)   NULL     COMMENT '계약구분(신규/갱신)',
    contract_term  VARCHAR(30)   NULL     COMMENT '계약기간',
    dedup_key      CHAR(64)      NOT NULL COMMENT '중복방지 해시(SHA-256)',
    json           JSON          NULL     COMMENT '국토부 원본 응답',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rent_dedup (dedup_key),
    KEY idx_rent_search (region_code, housing_type, deal_type, deal_ym),
    KEY idx_rent_ym (deal_ym),
    CONSTRAINT fk_rent_region FOREIGN KEY (region_code) REFERENCES region (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='실거래 전월세(4종 통합)';

-- =====================================================================
-- [회원 종속] member 참조
-- =====================================================================

-- 리프레시 토큰 --------------------------------------------------------
CREATE TABLE refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    member_id   BIGINT       NOT NULL COMMENT '회원 FK',
    token       VARCHAR(512) NOT NULL COMMENT 'Refresh Token 값(해시 저장)',
    expires_at  DATETIME     NOT NULL COMMENT '만료 일시',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token (token),
    KEY idx_refresh_member (member_id),
    CONSTRAINT fk_refresh_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='리프레시 토큰';

-- 약관 동의 이력 -------------------------------------------------------
CREATE TABLE member_agreement (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    member_id          BIGINT      NOT NULL COMMENT '회원 FK',
    agreement_type     VARCHAR(30) NOT NULL COMMENT 'SERVICE/PRIVACY/ASSET_LINK/MARKETING',
    is_agreed          BOOLEAN     NOT NULL COMMENT '동의 여부',
    agreement_version  VARCHAR(20) NOT NULL COMMENT '동의한 약관 버전',
    agreed_at          DATETIME    NOT NULL COMMENT '동의 시각',
    PRIMARY KEY (id),
    KEY idx_agreement_member (member_id),
    CONSTRAINT fk_agreement_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='약관 동의 이력';

-- 관심 정책 (member × policies) ---------------------------------------
CREATE TABLE member_saved_policies (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    member_id   BIGINT   NOT NULL COMMENT '사용자 FK',
    policy_id   BIGINT   NOT NULL COMMENT '정책 FK',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '관심 저장 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_saved_policy (member_id, policy_id),
    CONSTRAINT fk_saved_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_saved_policy FOREIGN KEY (policy_id) REFERENCES policies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='관심(저장) 정책';

-- 자산 현재값 캐시 -----------------------------------------------------
CREATE TABLE asset_summary (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    member_id        BIGINT   NOT NULL COMMENT '회원 FK',
    total_assets     BIGINT   NOT NULL DEFAULT 0 COMMENT '총자산 합계',
    loan_balance     BIGINT   NOT NULL DEFAULT 0 COMMENT '총 대출 잔액',
    monthly_savings  BIGINT   NOT NULL DEFAULT 0 COMMENT '월 저축 가능액',
    synced_at        DATETIME NULL     COMMENT '마지막 동기화 시각',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_summary_member (member_id),
    CONSTRAINT fk_asset_summary_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='자산 현재값 캐시(회원당 1행)';

-- 자산 월별 이력 (진단 이력 겸용) -------------------------------------
CREATE TABLE asset_snapshot (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    member_id       BIGINT      NOT NULL COMMENT '대상 회원 FK',
    snapshot_ym     VARCHAR(6)  NOT NULL COMMENT '스냅샷 연월 YYYYMM',
    total_assets    BIGINT      NOT NULL DEFAULT 0 COMMENT '그 달 총자산(asset_account.balance 합)',
    loan_balance    BIGINT      NOT NULL DEFAULT 0 COMMENT '그 달 총부채',
    net_assets      BIGINT      NOT NULL DEFAULT 0 COMMENT '그 달 순자산(total-loan)',
    monthly_savings BIGINT      NOT NULL DEFAULT 0 COMMENT '월 저축액',
    income_bracket  VARCHAR(20) NULL     COMMENT '그 시점 소득 구간(member값 복사)',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성(그 달 첫 동기화)',
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_member_ym (member_id, snapshot_ym),
    CONSTRAINT fk_snapshot_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='자산 월별 이력(upsert)';

-- CODEF 연동 (회원당 1개) ---------------------------------------------
CREATE TABLE connected_account (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    member_id         BIGINT       NOT NULL COMMENT '회원 FK(1:1)',
    connected_id      VARCHAR(512) NOT NULL COMMENT 'CODEF Connected ID(암호화)',
    connected_status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
    last_synced_at    DATETIME     NULL     COMMENT '마지막 동기화 성공 시각',
    sync_status       VARCHAR(20)  NULL     COMMENT 'SUCCESS/FAILED/IN_PROGRESS',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connected_member (member_id),
    CONSTRAINT fk_connected_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CODEF 연동(회원당 1개)';

-- 목표 본체 -----------------------------------------------------------
CREATE TABLE goal (
    id                            BIGINT      NOT NULL AUTO_INCREMENT,
    member_id                     BIGINT      NOT NULL COMMENT '회원 FK',
    goal_type                     VARCHAR(20) NOT NULL DEFAULT 'HOUSING' COMMENT '목표 종류(현재 HOUSING)',
    target_amount                 BIGINT      NOT NULL COMMENT '목표 금액(설정 시점 기준 고정, 자동 갱신 없음)',
    target_date                   DATE        NOT NULL COMMENT '희망 목표 시점',
    monthly_saving                BIGINT      NOT NULL COMMENT '월 저축액(사용자 입력, 수정 가능)',
    status                        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ACHIEVED/ARCHIVED',
    market_alert_dismissed_at     DATETIME    NULL     COMMENT '[현재 목표 유지] 클릭 시각(시스템 자동 기록)',
    market_alert_dismissed_price  BIGINT      NULL     COMMENT '그때 배너에 표시된 시세(시스템 자동 기록)',
    created_at                    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_goal_member_status (member_id, status),
    CONSTRAINT fk_goal_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='목표 본체(금액 고정)';

-- =====================================================================
-- [연동 종속] connected_account 참조
-- =====================================================================

-- 연동 기관 (연동 1개당 N개) ------------------------------------------
CREATE TABLE connected_institution (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    connected_account_id  BIGINT      NOT NULL COMMENT '소속 Connected ID FK',
    institution_code      VARCHAR(10) NOT NULL COMMENT 'CODEF 기관코드',
    institution_name      VARCHAR(50) NOT NULL COMMENT '기관명',
    institution_type      VARCHAR(20) NOT NULL COMMENT 'BANK/STOCK/CARD/LOAN',
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/AUTH_EXPIRED/ERROR',
    last_synced_at        DATETIME    NULL     COMMENT '이 기관 마지막 동기화 시각',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conn_inst (connected_account_id, institution_code),
    CONSTRAINT fk_conn_inst_account FOREIGN KEY (connected_account_id) REFERENCES connected_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='연동 기관';

-- =====================================================================
-- [기관 종속] connected_institution 참조
-- =====================================================================

-- 자산 계좌 (예적금·주식) ---------------------------------------------
CREATE TABLE asset_account (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    connected_institution_id  BIGINT       NOT NULL COMMENT '연결된 기관 FK',
    account_type              VARCHAR(20)  NOT NULL COMMENT 'DEPOSIT/SAVINGS/STOCK/FUND',
    asset_category            VARCHAR(20)  NOT NULL COMMENT 'CASH_ASSET/INVESTMENT',
    account_display           VARCHAR(50)  NULL     COMMENT '표시용 계좌번호',
    product_name              VARCHAR(100) NULL     COMMENT '계좌명/상품명',
    balance                   BIGINT       NOT NULL DEFAULT 0 COMMENT '현재가치 환산액',
    raw_response              JSON         NULL     COMMENT '원본 응답',
    deleted_at                DATETIME     NULL     COMMENT 'soft delete',
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_asset_account_inst (connected_institution_id),
    KEY idx_asset_account_category (asset_category),
    CONSTRAINT fk_asset_account_inst FOREIGN KEY (connected_institution_id) REFERENCES connected_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='자산 계좌(예적금·주식)';

-- 대출 계좌 -----------------------------------------------------------
CREATE TABLE loan_account (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    connected_institution_id  BIGINT       NOT NULL COMMENT '연결된 기관 FK',
    loan_name                 VARCHAR(100) NULL     COMMENT '대출 상품명',
    account_display           VARCHAR(50)  NULL     COMMENT '표시용 계좌번호',
    loan_balance              BIGINT       NOT NULL DEFAULT 0 COMMENT '대출 잔액(원)',
    raw_response              JSON         NULL     COMMENT '원본 응답',
    deleted_at                DATETIME     NULL     COMMENT 'soft delete',
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_loan_account_inst (connected_institution_id),
    CONSTRAINT fk_loan_account_inst FOREIGN KEY (connected_institution_id) REFERENCES connected_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='대출 계좌';

-- =====================================================================
-- [목표 종속] goal 참조
-- =====================================================================

-- 주거 목표 상세 (1:1) ------------------------------------------------
CREATE TABLE goal_housing (
    goal_id            BIGINT       NOT NULL COMMENT 'goal FK(1:1)',
    regions            JSON         NOT NULL COMMENT '지역 코드 배열 ["11650","11680"]',
    housing_types       JSON         NOT NULL COMMENT '다중 선택 주거 형태',
    deal_types         JSON         NOT NULL COMMENT '다중 선택 거래 유형',
    area_min           INT          NULL     COMMENT '희망 최소 평수',
    area_max           INT          NULL     COMMENT '희망 최대 평수',
    deposit_min        BIGINT       NULL     COMMENT '희망 최소 보증금',
    deposit_max        BIGINT       NULL     COMMENT '희망 최대 보증금',
    monthly_rent_min   BIGINT       NULL     COMMENT '희망 최소 월세',
    monthly_rent_max   BIGINT       NULL     COMMENT '희망 최대 월세',
    PRIMARY KEY (goal_id),
    CONSTRAINT fk_goal_housing_goal FOREIGN KEY (goal_id) REFERENCES goal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주거 목표 상세';

-- 월별 저축 기록 ------------------------------------------------------
CREATE TABLE saving_record (
    id             BIGINT     NOT NULL AUTO_INCREMENT,
    goal_id        BIGINT     NOT NULL COMMENT 'goal FK',
    record_ym      VARCHAR(6) NOT NULL COMMENT '기록 연월 YYYYMM',
    target_saving  BIGINT     NOT NULL COMMENT '그 달 목표 저축액(복사 고정)',
    actual_saving  BIGINT     NOT NULL COMMENT '그 달 실제 저축액(기본=목표, 수정가능)',
    is_modified    BOOLEAN    NOT NULL DEFAULT FALSE COMMENT '사용자 수정 여부',
    created_at     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_saving_goal_ym (goal_id, record_ym),
    CONSTRAINT fk_saving_goal FOREIGN KEY (goal_id) REFERENCES goal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='월별 저축 기록';
