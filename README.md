# nagasseum-cheongnyeon (backend)

MZ세대의 독립 준비를 위한 자산 관리 플랫폼 **나갔음 청년**의 백엔드 레포지토리입니다.

> 개발 환경 최초 세팅은 **[Onboarding.md](./Onboarding.md)** 를 순서대로 따라오세요.

---

## 서비스 소개

청년이 전월세 독립 목표를 세우고, 금융 자산을 연동해 달성률을 추적하며, 다른 사용자와 비교하는 플랫폼입니다.

| 핵심 기능 | 설명                                                |
| --- |---------------------------------------------------|
| 자산 연동 | CODEF API로 은행, 증권, 카드, 대출 계좌를 실시간 동기화             |
| 독립 목표 | 지역, 주택유형, 전월세 조건 설정 → 달성률 및 잔여 기간 계산              |
| 목표 추천 | 자산, 저축 여력, 희망 조건 기반으로 3종 알고리즘이 서로 다른 관점의 목표 카드 제시 |
| 몬테카를로 시뮬레이션 | GBM 기반 1만 경로 시뮬레이션으로 목표 시점 주택 가격 분포 및 달성 확률 제공    |
| 시세 변동 알림 | 실거래 중위가 변화 감지 → 목표 재조정 배너 노출                      |
| 또래 비교 | 동일 코호트(나이, 소득 분위) 자산과 목표 달성률 비교                   |
| 카카오 로그인 | OAuth2 + JWT (Access / Refresh Token)             |

---

## 기술 스택

| 구분           | 기술                                      |
|--------------|-----------------------------------------|
| Language     | Java 17                                 |
| Framework    | Spring Framework 5.3                    |
| SQL Mapper   | MyBatis                                 |
| Build        | Maven (WAR Packaging)                   |
| WAS          | Tomcat 9.0.118                          |
| DB           | MySQL 8.0 (AWS RDS)                     |
| Cache / Lock | Redis 7 (AWS ElastiCache)               |
| Auth         | Kakao OAuth2 + JWT                      |
| External API | CODEF, 국토부, Kakao, Slack Webhook |
| CI/CD        | GitHub Actions → Docker Hub → EC2       |

### 기술 선택 배경

1. Spring Legacy (SpringBoot 사용 불가): 부트캠프 최종 프로젝트 제약 조건입니다.


2. MyBatis: 복잡한 집계 쿼리(다른 사용자 비교, 자산 요약)가 많고, SQL을 직접 제어해야 하는 상황에 적합합니다. 최종 프로젝트 제약 조건이기도 합니다.


3. 몬테카를로 시뮬레이션: 목표 시점의 주택 가격을 단순 CAGR로 예측하면 불확실성을 표현할 수 없습니다. GBM(기하 브라운 운동) 기반 시뮬레이션으로 P5 / P50 / P95 분포와 달성 확률을 함께 제공합니다. 가격 모델(μ, σ)은 국토부 실거래 시계열에 OLS 회귀를 적용해 추정합니다.

---

## 핵심 로직

### 자산 예산 산출

연동한 자산을 목표 시점의 예산으로 환산합니다. 자산을 성격에 따라 나눠 각기 다른 가정으로 미래가치를 계산합니다. (`AssetSummaryServiceImpl`, `BudgetCalculator`)

- **이자부 자산(예적금)**: 연 5% 복리 거치식으로 성장
- **정액 인정 자산**: 투자/주식은 평가액의 70%만 인정(변동성 반영), 현금과 수동 등록 자산은 원금 그대로 반영
- **월 저축액**: 연 5% 복리 적립식 미래가치(ordinary annuity)
- **대출**: 순자산에서 잔액 차감

이렇게 나온 목표 시점 예산이 몬테카를로의 달성 확률 판정과 추천의 후보 선택 기준이 됩니다.

### 가격 모델 (Price Model)

목표 시점의 주택 가격을 예측하기 위한 기초 모델입니다. 지역, 주택유형, 거래유형, 평수 구간별로
최근 36개월 실거래에서 월별 평당 보증금 중앙값 시계열을 만들고(월 표본 5건 미만 제외, 유효 6개월 이상 필요),
여기에 OLS 회귀를 적용해 연간 drift(μ), 변동성(σ), CAGR을 추정합니다. 결과는 Redis에 캐싱합니다(TTL 12시간).
추천처럼 여러 조합을 한 번에 다룰 때는 MGET 조회 + (주택유형, 거래유형) 유니크 쌍 단위 배치 쿼리로 DB 왕복을 줄입니다.

### 몬테카를로 시뮬레이션

가격 모델의 μ, σ를 GBM에 넣어 목표 시점 가격 경로를 1만 회 시뮬레이션하고,
P5 / P50 / P95 분포와 목표 예산 대비 달성 확률을 산출합니다. 결과는 `goalId` 기준으로 Redis에 캐싱하며,
목표 조건이 바뀌지 않으면 재계산 없이 반환합니다.

### 목표 추천 엔진

자산, 저축 여력, 희망 조건을 받아 관점이 다른 3종 알고리즘을 실행하고, 각각을 하나의 추천 카드로 내려줍니다.

| 알고리즘 | 관점 |
| --- | --- |
| Realistic | 목표 시점을 고정한 채 그 안에 도달 가능한 조건을 탐색 |
| HoldOut | Realistic 결과를 기준점으로, 조금 더 기다리면 얻을 수 있는 한 단계 위 조건 제시 |
| Preference | 사용자 희망 조건을 그대로 시세로 환산 (감당 여부는 판단하지 않음) |

**후보군 40조합**: 사용자가 조건을 비우면 평수 5구간 × 주택유형 4종 × 거래유형 2종 = 40개 조합을 후보로 펼치고,
각 조합의 실거래 중앙값 중 예산에 가장 근접한 곳을 고릅니다.

**월세의 전세 환산보증금**: 월세는 보증금이 작다고 싼 게 아니라, 전세도 보증금이 묶여 이자를 못 버는 비용이 있습니다.
그래서 비교, 판정 시 월세를 환산보증금 = 보증금 + (월세 × 12 ÷ 0.05)로 바꿔 전세와 같은 축에서 비교합니다.
단, 화면에 표시하는 목표 금액은 사용자가 실제로 모아야 하는 보증금이며, 환산값은 내부 비교용으로만 씁니다.

**2-Phase 실행**: HoldOut은 Realistic 결과를 기준점으로 쓰므로, Phase 1에서 Realistic, Preference를 병렬로 돌리고
Phase 2에서 Realistic 완료 후 HoldOut을 실행합니다(`algorithmExecutor` 전용 스레드 풀). 알고리즘 하나가 실패해도
나머지 추천은 내려주며, 전부 실패할 때만 예외를 던집니다. 결과는 재진입, 새로고침을 위해 Redis에 캐싱합니다.

### 자산 연동 보안

CODEF 연동은 금융기관 자격증명을 다루므로 다음 처리를 강제합니다.

- **Connected ID 암호화**: CODEF가 발급하는 Connected ID(회원당 1:1)는 평문으로 저장하지 않고 AES-256-GCM으로 암호화해 보관합니다. (`common/security/AesEncryptor`)


- **계좌 비밀번호 RSA 암호화**: 연동 시 입력한 비밀번호는 CODEF로 보내기 전 RSA로 암호화하며, 필드·로그·DB 어디에도 평문을 남기지 않습니다. 로그 마스킹(`@ToString(exclude = "password")`)도 필수입니다. (`external/codef/CodefRsaEncryptor`)


- **회원별 분산 락**: 여러 기관을 동시에 연동하면 Connected ID가 중복 발급될 수 있어, `asset:link:lock:{memberId}` 키로 회원 단위 Redis 락(TTL 30초)을 걸어 동시 연동을 직렬화합니다. (`AssetConnectionServiceImpl`)

동기화 시 CODEF 응답에 없는 계좌는 소프트 삭제 컬럼 없이 물리적으로 삭제해 최신 상태를 반영합니다. (`AssetSyncServiceImpl`)

### 비동기 자산 동기화

여러 기관을 한 번에 연동하면 CODEF 왕복이 계좌 수만큼 쌓여 요청이 오래 걸립니다. 그래서 동기화는 요청 스레드를 붙잡지 않고 별도 잡으로 처리합니다.

- `POST /sync`: 잡을 시작하고 `jobId`를 즉시 반환 (요청은 여기서 끝남)
- `GET /sync/status/{jobId}`: 진행 상태를 폴링

프론트는 시작 응답을 받고 상태만 폴링하므로, 여러 개의 계좌 연동 지연이 사용자 화면을 막지 않습니다. (`AssetSyncJobStarter`, `AssetSyncJobRunner`)

### 국토부 실거래 배치 (멱등성)

국토부 API는 거래 고유 ID가 없습니다. 정보 조합으로만 식별되는 자연키라 수정 여부를 알 수 없고, 뒤늦게 추가되는 거래도 구분할 수 없습니다. 이 특수성 때문에 부분 갱신 대신 (지역 × 거래월 × 주택유형) 단위 DELETE → INSERT로 재적재합니다.

매 재적재는 `rent_sync_log`에 기록해 멱등성(재실행해도 결과 동일), 데이터 누락 방지, 자동 배치 재개를 보장합니다. 570만 건 규모를 매월 안정적으로 갱신하는 근거입니다. (`RentTransactionUnitSyncServiceImpl`, `RentSyncLogMapper`)

### 추천 성능 개선

서울시 전체 + 조건 미입력 진단은 후보가 40조합으로 폭발하며 실거래 테이블을 사실상 풀스캔했습니다(배포 570만 건에서 타임아웃). 핵심 병목은 SQL 인덱스 미스였습니다.

- `region_code LIKE '11%'`(2자리 시도 프리픽스)는 뒤 인덱스 컬럼(주택유형/거래유형/평수)을 못 타 → 시군구 IN 목록으로 전환
- `rent_transaction`에 area·deposit·monthly_rent를 더한 커버링 인덱스로 구간 필터 후 랜덤 룩업 제거

이 개선으로 해당 요청이 **약 5분 30초 → 2초대**로 줄었습니다.

---

## 아키텍처

### 도메인 의존 방향

```
goal ──→ asset
goal ──→ property
compare ──→ asset
compare ──→ goal
```

`asset`, `member`는 다른 도메인을 모릅니다. 역방향 참조는 철저히 배제했습니다.

### 레이어 구조

```
Controller / Scheduler
        ↓
     Service  (@Transactional)
        ↓
      Mapper   (@Mapper)
        ↓
        DB
```

- Controller는 Mapper를 직접 호출하지 않습니다.
- 다른 도메인에 접근할 때는 반드시 그 도메인의 서비스 계층을 통합니다.
- 외부 API 호출은 `external/` 패키지에서만 합니다.

### 배치 흐름 (매월 1일)

```
04:00  RentTransactionSyncScheduler  →  국토부 API 호출  →  rent_transaction 적재
04:00  AssetSyncScheduler  →  CODEF API 호출  →  자산 계좌 동기화 (매일)
05:00  GoalSnapshotScheduler  →  자산/목표 기반  →  goal_snapshot 기록 (다른 사용자와의 비교용)
06:00  GoalMarketTrendScheduler  →  실거래 중위가  →  Redis 캐시 갱신
```

---

## 패키지 구조

```
com.team.independence
├── auth/            카카오 OAuth2, JWT 인증
├── member/          회원 정보 관리
├── asset/           자산 연동 / 동기화 / 진단
├── goal/            독립 목표 설정 / 추적 / 시뮬레이션
├── compare/         비교
├── property/        전월세 실거래 조회 / 가격 모델
├── policy/          공공 정책 추천 (추후 개발 예정)
├── scheduler/       배치 스케줄러 (batch 프로필 전용)
├── external/        외부 API 클라이언트 (CODEF, Slack)
├── common/          공통 인프라 (응답 포맷, 예외, 보안)
└── config/          설정
```

---

## API 엔드포인트

<details>
<summary>인증 (/api/v1)</summary>

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/oauth/kakao/callback` | 카카오 OAuth 콜백 |
| POST | `/oauth/kakao/signup` | 추가 정보 입력 후 회원 가입 |
| POST | `/auth/refresh` | Access Token 재발급 |
| POST | `/auth/logout` | 로그아웃 (Refresh Token 폐기) |

</details>

<details>
<summary>회원 (/api/v1/members)</summary>

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/me` | 내 정보 조회 |
| PATCH | `/me` | 내 정보 수정 |
| PATCH | `/me/agreements` | 전체 약관 동의 |
| PATCH | `/me/agreements/{type}` | 개별 약관 동의 |
| GET | `/health` | 헬스체크 |

</details>

<details>
<summary>자산 (/api/v1/assets)</summary>

| 메서드 | 경로 | 설명               |
| --- | --- |------------------|
| GET | `/organizations` | 연동 가능 기관 목록      |
| POST | `/link` | 기관 계좌 연동 (CODEF) |
| GET | `/connections` | 연동된 기관 목록        |
| DELETE | `/connections/organizations/{code}` | 기관 연동 해제         |
| POST | `/sync` | 자산 동기화 시작 (비동기)  |
| GET | `/sync/status/{jobId}` | 동기화 상태 조회        |
| GET | `/summary` | 자산 요약            |
| GET | `/accounts` | 은행, 증권, 대출 계좌 목록 |
| GET | `/cards` | 카드 목록            |
| GET | `/manual` | 수동 자산 목록         |
| POST | `/manual` | 수동 자산 등록         |
| PUT | `/manual/{id}` | 수동 자산 수정         |
| DELETE | `/manual/{id}` | 수동 자산 삭제         |

</details>

<details>
<summary>목표 (/api/v1/goals)</summary>

| 메서드 | 경로 | 설명                                     |
| --- | --- |----------------------------------------|
| POST | `/diagnosis` | 목표 진단 (자산 기반 추천)                       |
| GET | `/recommendations` | 추천 목표 계산 (희망 조건은 쿼리 파라미터)              |
| GET | `/recommendation` | 직전 추천 결과 재조회 (결과 화면 재진입, 새로고침용)        |
| POST | `` | 목표 생성                                  |
| GET | `/active` | 진행 중(활성) 목표 조회                         |
| GET | `/{goalId}` | 목표 조회                                  |
| PUT | `/{goalId}` | 목표 수정                                  |
| DELETE | `/{goalId}` | 목표 삭제 (ARCHIVED 처리)                    |
| GET | `/{goalId}/detail` | 목표 상세, 달성률                             |
| GET | `/{goalId}/simulations/monthly-saving` | 월 저축액 시뮬레이션                            |
| GET | `/{goalId}/simulation` | GBM 몬테카를로 시뮬레이션 (P5 / P50 / P95, 달성확률) |
| GET | `/savings/current` | 현재 월 저축액 조회                            |
| PUT | `/savings/current` | 월 저축액만 변경                              |
| GET | `/market-trend` | 시세 변동 배너                               |
| GET | `/summary` | 목표 요약                                  |

</details>

<details>
<summary>비교 (/api/v1/comparison)</summary>

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `` | 종합 비교 |
| GET | `/assets` | 자산 비교 |
| GET | `/goals` | 목표 비교 |

</details>

<details>
<summary>매물 (/api/v1/properties)</summary>

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/median` | 지역, 주택유형별 전월세 중위가 |

</details>

<details>
<summary>관리 (/api/v1/admin/batch)</summary>

> 인증 없이 호출 가능. 인증 완성 후 관리자 권한 체크 예정

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/all` | 전체 배치 순서대로 실행 (초기 데이터 세팅 시 사용) |
| POST | `/rent-sync` | 전월세 실거래 동기화만 실행 |
| POST | `/snapshots` | 이번 달 목표 스냅샷 생성만 실행 |
| POST | `/market-trends` | 시세 변화 캐시 갱신만 실행 |

</details>

---

## 응답 포맷

```json
// 성공
{ "success": true, "data": { ... }, "error": null }

// 실패
{ "success": false, "data": null, "error": { "code": "MEMBER_001", "message": "회원을 찾을 수 없습니다." } }
```

---

## 인프라 구성 (AWS)

![시스템 아키텍처](src/main/resources/architecture/architecture.svg)


---

## 초기 데이터 세팅

인프라를 새로 구축했거나 DB가 비어 있을 때 아래 순서대로 실행합니다.

### 1. DDL 실행 (EC2에서)

```bash
# EC2(SSM)에서 SQL 파일 다운로드
curl -o /tmp/full_schema.sql https://raw.githubusercontent.com/kb-final/nagasseum-cheongnyeon_backend/develop/sql/full_schema.sql
curl -o /tmp/institution_master.sql https://raw.githubusercontent.com/kb-final/nagasseum-cheongnyeon_backend/develop/sql/institution_master.sql
curl -o /tmp/region_initialize.sql https://raw.githubusercontent.com/kb-final/nagasseum-cheongnyeon_backend/develop/sql/region_initialize.sql

# RDS에 순서대로 실행
mysql -h {RDS엔드포인트} -u {유저명} -p{비밀번호} independence < /tmp/full_schema.sql
mysql -h {RDS엔드포인트} -u {유저명} -p{비밀번호} independence < /tmp/institution_master.sql
mysql -h {RDS엔드포인트} -u {유저명} -p{비밀번호} independence < /tmp/region_initialize.sql
```

### 2. 실거래 데이터 수집 (배치 수동 트리거)

월 1일 스케줄 실행 전이라면 API로 수동 트리거합니다.

```bash
curl -X POST https://{API서버주소}/api/v1/admin/batch/all
```

실거래 동기화(6개월 × 전체 지역 × 4종)는 수십 분이 소요됩니다.
504 응답이 와도 서버에서 배치가 계속 실행되므로 서버 로그로 완료 여부를 확인하세요.

```bash
sudo docker logs -f $(sudo docker ps -q) 2>&1 | grep "배치"
```

---

## 배포

`develop` 브랜치에 push되면 GitHub Actions가 자동으로 빌드하고 Docker Hub에 이미지를 푸시합니다.

```
develop push
  → ./mvnw clean package -DskipTests (JDK 17)
  → docker build (tomcat:9-jdk17-temurin 기반)
  → Docker Hub push (:latest, :{SHA})
```

EC2에서는 새 이미지를 pull 받아 컨테이너를 재시작합니다.

### 로컬에서 Docker 이미지로 실행

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw clean package -DskipTests
docker build -t nagasseum-backend .

docker run -d --name nagasseum-backend --env-file .env -p 8080:8080 nagasseum-backend
```

---

## 외부 연동

| 서비스 | 용도                                 |
| --- |------------------------------------|
| CODEF | 은행, 증권, 카드 계좌 조회 및 Connected ID 관리 |
| MOLIT (국토부) | 전월세 실거래 수집 (아파트 / 연립 / 오피스텔 / 단독)  |
| Kakao | OAuth2 로그인 및 사용자 정보 조회             |
| Slack Webhook | 배치 실패 알림                           |

`local` 프로필에서 CODEF는 Mock 클라이언트(`CodefMockClient`)로 대체됩니다.
