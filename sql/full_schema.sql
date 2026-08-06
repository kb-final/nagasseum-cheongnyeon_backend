-- =====================================================================
-- 나갔음 청년 - 전체 스키마 (20개 테이블)
-- MySQL 8.0 / utf8mb4 / 금액은 BIGINT(원 단위) / 타임존 KST
-- 생성 순서: 참조되는(부모) 테이블 → 참조하는(자식) 테이블
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- =====================================================================
-- [기준·마스터 테이블]
-- =====================================================================

-- 회원 -----------------------------------------------------------------
CREATE TABLE member (
    id                        BIGINT      NOT NULL AUTO_INCREMENT,
    kakao_id                  VARCHAR(50) NOT NULL COMMENT '카카오 고유 회원번호(로그인 키)',
    nickname                  VARCHAR(20) NOT NULL COMMENT '표시 이름',
    birth_date                DATE        NOT NULL COMMENT '생년월일(연령 계산·또래 비교 필터)',
    monthly_income            BIGINT      NULL     COMMENT '월 소득',
    occupation_type           VARCHAR(30) NULL     COMMENT '직업군',
    income_bracket            VARCHAR(20) NULL     COMMENT '소득 분위(예: INCOME_100_120)',
    income_bracket_updated_at DATETIME    NULL     COMMENT '소득 분위 마지막 수정일',
    created_at                DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='지역 코드 마스터(시군구)';

-- 연동 가능 기관 마스터 --------------------------------------------------
CREATE TABLE institution (
    code               VARCHAR(10)  NOT NULL COMMENT 'CODEF 기관코드(요청의 organization)',
    name               VARCHAR(50)  NOT NULL COMMENT '표시명(예: KB국민은행)',
    institution_type   VARCHAR(20)  NOT NULL COMMENT 'BANK / STOCK',
    business_type      VARCHAR(10)  NOT NULL COMMENT 'CODEF 업권 코드(BK / ST)',
    login_type         VARCHAR(10)  NOT NULL DEFAULT '1' COMMENT '1=아이디 방식(고정)',
    product_label      VARCHAR(50)  NOT NULL COMMENT '카드 부제(예: 예적금 · 대출)',
    logo_url           VARCHAR(255) NULL     COMMENT '로고 이미지 경로',
    extra_field_schema JSON         NULL     COMMENT '기관별 추가 입력 항목. NULL이면 기본 폼(ID/PW/생년월일)',
    display_order      INT          NOT NULL DEFAULT 0 COMMENT '목록 정렬 순서',
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '연동 가능 여부(점검 중이면 false)',
    PRIMARY KEY (code),
    KEY idx_institution_active (is_active, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='연동 가능 기관 마스터';

-- 정책 -----------------------------------------------------------------
CREATE TABLE policies (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    source_api            VARCHAR(50)  NOT NULL COMMENT 'API 출처(YOUTH / GOV24)',
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
    extra                 JSON         NULL     COMMENT '원본 API 필드 보존용',
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '현행 정책 여부',
    applicable_goal_types VARCHAR(100) NOT NULL DEFAULT 'HOUSING' COMMENT '목표유형 필터(콤마구분)',
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
    housing_type   VARCHAR(20)   NOT NULL COMMENT 'APT / ROW_HOUSE / OFFICETEL / DETACHED',
    dong_name      VARCHAR(50)   NOT NULL COMMENT '법정동',
    jibun          VARCHAR(30)   NULL     COMMENT '지번(단독/다가구 미제공)',
    complex_name   VARCHAR(100)  NULL     COMMENT '단지명(단독/다가구 미제공)',
    area           DECIMAL(10,2) NOT NULL COMMENT '전용면적 / 연면적(단독다가구)',
    deal_type      VARCHAR(10)   NOT NULL COMMENT 'JEONSE / WOLSE (monthly_rent=0이면 JEONSE)',
    deposit        BIGINT        NOT NULL DEFAULT 0 COMMENT '보증금(원)',
    monthly_rent   BIGINT        NOT NULL DEFAULT 0 COMMENT '월세(원, 전세=0)',
    floor          INT           NULL     COMMENT '층(단독/다가구 미제공)',
    build_year     INT           NULL     COMMENT '건축년도(빈 값으로 오는 거래 존재)',
    deal_ym        VARCHAR(6)    NOT NULL COMMENT '계약년월 YYYYMM',
    deal_day       VARCHAR(2)    NOT NULL COMMENT '계약일',
    contract_type  VARCHAR(20)   NULL     COMMENT '갱신 / 신규(구 데이터는 빈 값)',
    contract_term  VARCHAR(30)   NULL     COMMENT '계약기간(구 데이터는 빈 값)',
    json           JSON          NOT NULL COMMENT '국토부 원본 응답',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- (지역, 연월, 유형) 단위 재적재 시 DELETE 대상을 구간으로 좁힌다.
    -- 최좌측 프리픽스가 region_code라 fk_rent_region의 인덱스 요건도 함께 충족.
    KEY idx_rent_reload (region_code, deal_ym, housing_type),
    CONSTRAINT fk_rent_region FOREIGN KEY (region_code) REFERENCES region (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='실거래 전월세(4종 통합)';

-- 수집 이력
-- 최초 수집/증분 수집을 코드에서 분기하지 않고, 조합별 성공 여부로 판단하기 위한 테이블.
-- 한 번 쌓인 이력은 지우지 않는다(지역 268 × 4종 × 누적 개월수만큼 늘어난다).
CREATE TABLE rent_sync_log (
    region_code   VARCHAR(5)  NOT NULL COMMENT '지역코드(FK 미설정: 이력은 region 삭제와 무관하게 보존)',
    deal_ym       VARCHAR(6)  NOT NULL COMMENT '계약년월 YYYYMM',
    housing_type  VARCHAR(20) NOT NULL COMMENT 'APT / ROW_HOUSE / OFFICETEL / DETACHED',
    is_success    BOOLEAN     NOT NULL COMMENT '수집 성공 여부(false면 다음 실행에서 재시도)',
    inserted_cnt  INT         NOT NULL DEFAULT 0 COMMENT '마지막 적재 건수',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 수집 시각',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수집 시각',
    PRIMARY KEY (region_code, deal_ym, housing_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='지역-연월-유형 단위 수집 이력';

-- =====================================================================
-- [회원 종속]
-- =====================================================================

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

CREATE TABLE member_agreement (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    member_id         BIGINT      NOT NULL COMMENT '회원 FK',
    agreement_type    VARCHAR(30) NOT NULL COMMENT 'SERVICE / PRIVACY / ASSET_LINK / MARKETING',
    is_agreed         BOOLEAN     NOT NULL COMMENT '동의 여부',
    agreement_version VARCHAR(20) NOT NULL COMMENT '동의한 약관 버전',
    agreed_at         DATETIME    NOT NULL COMMENT '동의 시각',
    PRIMARY KEY (id),
    KEY idx_agreement_member (member_id),
    CONSTRAINT fk_agreement_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='약관 동의 이력';

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

-- 수동 입력 자산 (연동으로 못 가져오는 자산) ---------------------------
CREATE TABLE manual_assets (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL COMMENT '회원 FK',
    asset_type  VARCHAR(20) NOT NULL COMMENT '자산 유형(DEPOSIT: 현재 거주 보증금)',
    amount      BIGINT      NOT NULL DEFAULT 0 COMMENT '자산 금액(원)',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_manual_member (member_id),
    -- 회원당 유형별 1건으로 제한할 경우 아래 주석 해제 (팀 결정 사항)
    -- UNIQUE KEY uk_manual_member_type (member_id, asset_type),
    CONSTRAINT fk_manual_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='수동 입력 자산';

CREATE TABLE asset_summary (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    member_id        BIGINT   NOT NULL COMMENT '회원 FK(1:1)',
    total_assets     BIGINT   NOT NULL DEFAULT 0 COMMENT '총자산 합계',
    loan_balance     BIGINT   NOT NULL DEFAULT 0 COMMENT '총 대출 잔액',
    monthly_savings  BIGINT   NOT NULL DEFAULT 0 COMMENT '월 저축액',
    synced_at        DATETIME NULL     COMMENT '마지막 동기화 시각',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_summary_member (member_id),
    CONSTRAINT fk_asset_summary_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='자산 현재값 캐시(회원당 1행)';

CREATE TABLE asset_snapshot (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    member_id       BIGINT      NOT NULL COMMENT '대상 회원 FK',
    snapshot_ym     VARCHAR(6)  NOT NULL COMMENT '스냅샷 연월 YYYYMM',
    total_assets    BIGINT      NOT NULL DEFAULT 0 COMMENT '그 달 총자산(asset_account.current_value 합산)',
    loan_balance    BIGINT      NOT NULL DEFAULT 0 COMMENT '그 달 총부채',
    net_assets      BIGINT      NOT NULL DEFAULT 0 COMMENT '그 달 순자산(total - loan)',
    monthly_savings BIGINT      NOT NULL DEFAULT 0 COMMENT '월 저축액',
    income_bracket  VARCHAR(20) NULL     COMMENT '그 시점 소득 구간(member 값 복사)',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_member_ym (member_id, snapshot_ym),
    CONSTRAINT fk_snapshot_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='자산 월별 이력';

CREATE TABLE connected_account (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    member_id         BIGINT       NOT NULL COMMENT '회원 FK(1:1)',
    connected_id      VARCHAR(512) NOT NULL COMMENT 'CODEF Connected ID(암호화 필수)',
    birth_date        VARCHAR(6)   NULL     COMMENT '생년월일 YYMMDD (계좌 조회 API 재사용)',
    connected_status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
    last_synced_at    DATETIME     NULL     COMMENT '마지막 동기화 성공 시각(최초 등록 시 NULL)',
    sync_status       VARCHAR(20)  NULL     COMMENT 'SUCCESS/FAILED/IN_PROGRESS',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connected_member (member_id),
    CONSTRAINT fk_connected_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CODEF 연동(회원당 1개)';

CREATE TABLE goal (
    id                           BIGINT      NOT NULL AUTO_INCREMENT,
    member_id                    BIGINT      NOT NULL COMMENT '회원 FK',
    goal_type                    VARCHAR(20) NOT NULL DEFAULT 'HOUSING' COMMENT '목표 종류',
    target_amount                BIGINT      NOT NULL COMMENT '목표 금액(설정 시점 고정, 자동 갱신 없음)',
    target_rent_middle_amount    BIGINT      NOT NULL COMMENT '목표 설정 당시 매물 중앙값(시세 알림 배너 비교 기준)',
    target_date                  DATE        NOT NULL COMMENT '희망 목표 시점',
    monthly_saving               BIGINT      NOT NULL COMMENT '월 저축액(사용자 입력, 수정 가능)',
    status                       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ACHIEVED/ARCHIVED',
    market_alert_dismissed_at    DATETIME    NULL     COMMENT '[현재 목표 유지] 클릭 시각(시스템 자동 기록)',
    market_alert_dismissed_price BIGINT      NULL     COMMENT '그때 배너에 표시된 시세(시스템 자동 기록)',
    created_at                   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_goal_member_status (member_id, status),
    CONSTRAINT fk_goal_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='목표 본체(금액 고정)';

-- =====================================================================
-- [또래 비교]
-- =====================================================================

CREATE TABLE goal_snapshot (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    member_id        BIGINT       NOT NULL COMMENT '대상 회원 FK',
    goal_id          BIGINT       NOT NULL COMMENT '대상 목표 FK',
    region_code      VARCHAR(5)   NOT NULL COMMENT '희망 지역 FK → 인기 지역 TOP 3',
    snapshot_ym      VARCHAR(6)   NOT NULL COMMENT '집계 기준월 YYYYMM',
    age              INT          NOT NULL COMMENT '그 시점 만 나이(고정) → 코호트 연령 필터',
    net_assets       BIGINT       NOT NULL COMMENT '그 시점 순자산 → 코호트 자산 필터 기준값',
    goal_type        VARCHAR(20)  NOT NULL COMMENT '목표 종류(현재 HOUSING)',
    housing_type     VARCHAR(20)  NOT NULL COMMENT '주거 형태 → 향후 분포 확장 대비',
    deal_type        VARCHAR(10)  NOT NULL COMMENT '거래 유형 → 목표 유형 분포',
    target_amount    BIGINT       NOT NULL COMMENT '목표 금액 → 평균 목표 자산',
    achievement_rate DECIMAL(5,2) NOT NULL COMMENT '그 시점 달성률(%) → 달성률 분포',
    prep_months      INT          NOT NULL COMMENT '준비 기간(개월) → 평균 준비 기간',
    monthly_saving   BIGINT       NOT NULL COMMENT '그 시점 월 저축액 → 저축액 구간',
    monthly_income   BIGINT       NULL     COMMENT '월 소득',
    income_bracket   VARCHAR(20)  NULL     COMMENT '소득 분위',
    occupation_type  VARCHAR(30)  NULL     COMMENT '직업군',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_goal_snapshot_member_ym (member_id, snapshot_ym),
    KEY idx_goal_snapshot_cohort (snapshot_ym, net_assets, age),
    KEY idx_goal_snapshot_region (region_code),
    CONSTRAINT fk_goal_snapshot_member FOREIGN KEY (member_id)   REFERENCES member (id),
    CONSTRAINT fk_goal_snapshot_goal   FOREIGN KEY (goal_id)     REFERENCES goal (id),
    CONSTRAINT fk_goal_snapshot_region FOREIGN KEY (region_code) REFERENCES region (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='또래 비교 집계용 목표 스냅샷';

-- =====================================================================
-- [연동·기관 종속]
-- =====================================================================

CREATE TABLE connected_institution (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    connected_account_id BIGINT       NOT NULL COMMENT '소속 Connected ID FK',
    institution_code     VARCHAR(10)  NOT NULL COMMENT '기관 FK → institution.code',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/AUTH_EXPIRED/ERROR',
    last_synced_at       DATETIME     NULL     COMMENT '이 기관 마지막 동기화 시각',
    last_error_code      VARCHAR(30)  NULL     COMMENT 'CODEF 실패 코드(비밀번호 오류, 계정 잠김 등)',
    last_error_message   VARCHAR(255) NULL     COMMENT '사용자 안내 문구',
    last_attempted_at    DATETIME     NULL     COMMENT '마지막 연동 시도 시각(성공·실패 무관)',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conn_inst (connected_account_id, institution_code),
    KEY idx_conn_inst_code (institution_code),
    CONSTRAINT fk_conn_inst_account     FOREIGN KEY (connected_account_id) REFERENCES connected_account (id),
    CONSTRAINT fk_conn_inst_institution FOREIGN KEY (institution_code)     REFERENCES institution (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='연동 기관(기관별 상태·실패 관리)';

-- 자산 계좌 -------------------------------------------------------------
-- account_type   : 카드 부제 표시용 (자유입출금/예금/적금/청약/펀드/주식)
-- asset_category : 화면 섹션 그룹핑용 (5개 섹션)
CREATE TABLE asset_account (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    connected_institution_id BIGINT       NOT NULL COMMENT '연결된 기관 FK',
    account_type             VARCHAR(20)  NOT NULL COMMENT 'DEMAND:자유입출금 / DEPOSIT:예금 / SAVINGS:적금 / SUBSCRIPTION:청약 / FUND:펀드 / STOCK:주식',
    asset_category           VARCHAR(20)  NOT NULL COMMENT 'CASH / DEPOSIT_SAVINGS / INVESTMENT / SUBSCRIPTION / ETC',
    account_display          VARCHAR(50)  NOT NULL COMMENT '표시용 계좌번호',
    product_name             VARCHAR(100) NOT NULL COMMENT '계좌명/상품명',
    current_value            BIGINT       NULL     COMMENT '현재가치 환산액(예적금=잔액, 펀드/증권=평가금액+예수금). 기준가 미공시 시 NULL',
    valuation_amount         BIGINT       NULL     COMMENT '증권 평가금액(증권 계좌 전용)',
    deposit_received         BIGINT       NULL     COMMENT '예수금(증권 계좌 전용)',
    valuation_pl             BIGINT       NULL     COMMENT '평가손익(증권 계좌 전용)',
    purchase_amount          BIGINT       NULL     COMMENT '매입금액(증권 계좌 전용)',
    earnings_rate            DECIMAL(6,2) NULL     COMMENT '수익률(%, 음수 가능)',
    start_date               DATE         NULL     COMMENT '계좌 개설일(은행 계좌 전용)',
    maturity_date            DATE         NULL     COMMENT '만기일(은행 계좌 전용)',
    raw_response             JSON         NOT NULL COMMENT 'CODEF 원본 응답',
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_asset_account_inst (connected_institution_id),
    KEY idx_asset_account_category (asset_category),
    CONSTRAINT fk_asset_account_inst FOREIGN KEY (connected_institution_id) REFERENCES connected_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='자산 계좌. 동기화 시 응답에 없는 계좌는 물리 삭제';

CREATE TABLE loan_account (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    connected_institution_id BIGINT       NOT NULL COMMENT '연결된 기관 FK',
    loan_name                VARCHAR(100) NOT NULL COMMENT '대출 상품명',
    account_display          VARCHAR(50)  NOT NULL COMMENT '표시용 계좌번호',
    loan_balance             BIGINT       NOT NULL DEFAULT 0 COMMENT '대출 잔액(원)',
    start_date               DATE         NULL     COMMENT '대출 실행일',
    end_date                 DATE         NULL     COMMENT '대출 만기일',
    raw_response             JSON         NOT NULL COMMENT 'CODEF 원본 응답',
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_loan_account_inst (connected_institution_id),
    CONSTRAINT fk_loan_account_inst FOREIGN KEY (connected_institution_id) REFERENCES connected_institution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='대출 계좌(부채)';

-- =====================================================================
-- [목표 종속]
-- =====================================================================

CREATE TABLE goal_housing (
    goal_id          BIGINT      NOT NULL COMMENT 'goal FK(1:1)',
    region_code      VARCHAR(5)  NOT NULL COMMENT '희망 지역 FK → region.code',
    housing_type     VARCHAR(20) NOT NULL COMMENT '희망 주거 형태(아파트/오피스텔/연립다세대/단독다가구)',
    deal_type        VARCHAR(10) NOT NULL COMMENT '희망 거래 유형(전세/월세)',
    area_min         INT         NOT NULL COMMENT '희망 최소 평수',
    area_max         INT         NOT NULL COMMENT '희망 최대 평수',
    deposit_min      BIGINT      NOT NULL COMMENT '희망 최소 보증금',
    deposit_max      BIGINT      NOT NULL COMMENT '희망 최대 보증금',
    monthly_rent_min BIGINT      NOT NULL DEFAULT 0 COMMENT '희망 최소 월세(전세면 0)',
    monthly_rent_max BIGINT      NOT NULL DEFAULT 0 COMMENT '희망 최대 월세(전세면 0)',
    PRIMARY KEY (goal_id),
    KEY idx_goal_housing_region (region_code),
    CONSTRAINT fk_goal_housing_goal   FOREIGN KEY (goal_id)     REFERENCES goal (id),
    CONSTRAINT fk_goal_housing_region FOREIGN KEY (region_code) REFERENCES region (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주거 목표 상세(단일 선택)';

CREATE TABLE saving_record (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    goal_id       BIGINT     NOT NULL COMMENT 'goal FK',
    record_ym     VARCHAR(6) NOT NULL COMMENT '기록 연월 YYYYMM',
    target_saving BIGINT     NOT NULL COMMENT '그 달 목표 저축액(그 시점 monthly_saving 복사 고정)',
    actual_saving BIGINT     NOT NULL COMMENT '그 달 실제 저축액(기본=목표, 다른 달만 수정)',
    is_modified   BOOLEAN    NOT NULL DEFAULT FALSE COMMENT '사용자 수정 여부',
    created_at    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_saving_goal_ym (goal_id, record_ym),
    CONSTRAINT fk_saving_goal FOREIGN KEY (goal_id) REFERENCES goal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='월별 저축 기록';
