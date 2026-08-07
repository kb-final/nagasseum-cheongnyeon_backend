# nagasseum-cheongnyeon (backend)

MZ세대의 독립 준비를 위한 자산 관리 플랫폼 서비스 나갔음 청년의 백엔드 레포지토리입니다.

> (개발) 처음 세팅 시 **[Onboarding.md](./Onboarding.md)** 를 순서대로 따라오세요.

---

## 프로젝트 개요

청년이 독립 목표(전·월세)를 설정하고, 금융 자산을 연동해 달성률을 추적하며, 또래와 비교하는 플랫폼입니다.

| 핵심 기능 | 설명                                    |
| --- |---------------------------------------|
| 자산 연동 | CODEF API로 은행 / 증권 / 카드 계좌를 실시간 동기화   |
| 독립 목표 | 목표 지역 / 주택 유형 / 전월세 설정 → 달성률 계산       |
| 또래 비교 | 동일 코호트(나이 / 소득 분위) 자산, 목표 비교          |
| 실거래 조회 | 국토부 API로 전월세 실거래 중위가 제공               |
| 카카오 로그인 | OAuth2 + JWT (Access / Refresh Token) |

---

## 기술 스택

| 구분 | 기술                                    |
| --- |---------------------------------------|
| Language | Java 17                               |
| Framework | Spring Framework 5.3 (Spring Boot 아님) |
| ORM | MyBatis                               |
| Build | Maven (WAR 패키징)                       |
| WAS | Tomcat 9.0.118                        |
| DB | MySQL 8.0                             |
| Cache / Lock | Redis 7                               |
| Auth | Kakao OAuth2 + JWT                    |
| External API | CODEF, 국토부, Kakao, Slack Webhook      |
| CI/CD | GitHub Actions → Docker Hub           |

---

## ⚠️ 필수 환경

| 항목 | 버전      | 주의                              |
| --- |---------|---------------------------------|
| JDK | 17      | 23 이상이면 빌드 실패 (Lombok 호환)       |
| Tomcat | 9.0.118 | 10 이상이면 404 발생 (jakarta 네임스페이스) |
| Docker | 최신      | MySQL, Redis 실행용                |
| IntelliJ | Ultimate | Community는 Tomcat 연동 불가         |

Maven은 설치 불필요 (`./mvnw` 또는 IntelliJ 내장 사용)

---

## 빠른 시작

```bash
git clone https://github.com/kb-final/nagasseum-cheongnyeon.git
cd nagasseum-cheongnyeon
git checkout develop

cp .env.example .env  # 로컬은 기본값으로 동작
docker compose up -d  # MySQL + Redis

# JDK가 여러 버전 설치된 경우 17 명시 필요
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw clean package -DskipTests
```

IntelliJ에서 Tomcat Server(Local) 실행 구성 생성 후:
- Server 탭 → VM options: `-Dspring.profiles.active=local,api`
- Deployment 탭 → `war exploded` 추가 → Application context: `/`

### 동작 확인

```bash
curl http://localhost:8080/api/v1/members/health
# {"success":true,"data":"OK","error":null}
```

---

## 프로필 (api / batch)

같은 WAR를 실행 시 프로필만 달리해 두 역할로 띄웁니다.

| 프로필 | 활성화 | 역할 |
| --- | --- | --- |
| `api` | Controller, ApiConfig | 사용자 요청 처리 |
| `batch` | Scheduler, BatchConfig(@EnableScheduling) | 새벽 배치 |

Service, Mapper는 양쪽 모두 로딩됩니다.

### 배치 스케줄

| 스케줄러 | 실행 시각 | 역할 |
| --- | --- | --- |
| `AssetSyncScheduler` | 매일 새벽 4시 | 전체 회원 자산 동기화 |
| `RentTransactionSyncScheduler` | 매월 1일 새벽 4시 | 전월세 실거래 수집 (MOLIT) |
| `GoalSnapshotScheduler` | 매월 1일 새벽 5시 | 또래 비교용 목표 스냅샷 기록 |
| `GoalMarketTrendScheduler` | 매월 1일 새벽 6시 | 시세 변동 배너 데이터 갱신 |

---

## 패키지 구조

```
com.team.independence
├── auth/            카카오 OAuth2, JWT 인증
├── member/          회원 정보 관리
├── asset/           자산 연동 / 동기화 / 진단
├── goal/            독립 목표 설정 / 추적
├── compare/         또래 비교
├── property/        전월세 실거래 조회
├── scheduler/       배치 (batch 프로필 전용)
├── external/        외부 API 클라이언트 (CODEF, Slack)
├── common/          공통 인프라 (응답 포맷, 예외, 보안)
└── config/          설정
```

각 도메인 내부 구조:
```
{도메인}/
├── controller/   HTTP 요청, 응답 (로직 없음)
├── service/      비즈니스 로직 (@Transactional)
├── mapper/       DB 접근 인터페이스 (@Mapper)
├── domain/       테이블 매핑 객체
└── dto/          요청, 응답 전용 객체
```

### asset 도메인 — 서브패키지 구조

계좌 유형이 다양해 `service/`, `domain/`, `dto/`를 추가로 분리했습니다.

```
asset/
├── service/
│   ├── AssetConnectionService    CODEF 계좌 연결, 해제·목록
│   ├── BankAccountService        은행 계좌 조회
│   ├── StockAccountService       증권 계좌 조회
│   ├── LoanAccountService        대출 계좌 조회
│   ├── CardAccountService        카드 조회
│   ├── AssetSummaryService       자산 요약, 순자산 계산
│   ├── AssetSyncService          CODEF 동기화 (비동기)
│   ├── InstitutionService        금융 기관 목록
│   └── ManualAssetService        수동 자산 관리
├── domain/
│   ├── account/    AssetAccount, LoanAccount, CardAccount
│   ├── codef/      ConnectedAccount, ConnectedInstitution, Institution
│   ├── manual/     ManualAsset
│   └── summary/    AssetSummary
└── dto/
    ├── account/    계좌 조회 응답
    ├── connection/ 연결 요청, 응답
    ├── summary/    자산 요약 응답
    ├── sync/       동기화 상태 응답
    └── manual/     수동 자산 요청, 응답
```

---

## API 엔드포인트

### 인증 (`/api/v1`)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/oauth/kakao/callback` | 카카오 OAuth 콜백 |
| POST | `/oauth/kakao/signup` | 추가 정보 입력 후 회원 가입 |
| POST | `/auth/refresh` | Access Token 재발급 |
| POST | `/auth/logout` | 로그아웃 (Refresh Token 폐기) |

&nbsp;

### 회원 (`/api/v1/members`)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/me` | 내 정보 조회 |
| PATCH | `/me` | 내 정보 수정 |
| PATCH | `/me/agreements` | 전체 약관 동의 |
| PATCH | `/me/agreements/{type}` | 개별 약관 동의 |
| GET | `/health` | 헬스체크 |

&nbsp;

### 자산 (`/api/v1/assets`)

| 메서드 | 경로 | 설명                 |
| --- | --- |--------------------|
| GET | `/organizations` | 연동 가능 기관 목록        |
| POST | `/link` | 기관 계좌 연동 (CODEF)   |
| GET | `/connections` | 연동된 기관 목록          |
| DELETE | `/connections/organizations/{code}` | 기관 연동 해제           |
| POST | `/sync` | 자산 동기화 시작 (비동기)    |
| GET | `/sync/status/{jobId}` | 동기화 상태 조회          |
| GET | `/summary` | 자산 요약              |
| GET | `/accounts` | 은행 / 증권 / 대출 계좌 목록 |
| GET | `/cards` | 카드 목록              |
| GET | `/manual` | 수동 자산 목록           |
| POST | `/manual` | 수동 자산 등록           |
| PUT | `/manual/{id}` | 수동 자산 수정           |
| DELETE | `/manual/{id}` | 수동 자산 삭제           |

&nbsp;

### 목표 (`/api/v1/goals`)

| 메서드 | 경로                                   | 설명 |
| --- |--------------------------------------| --- |
| POST | `/diagnosis`                         | 목표 진단 (자산 기반 추천) |
| POST |                                      | 목표 생성 |
| PUT | `/{goalId}`                          | 목표 수정 |
| DELETE | `/{goalId}`                          | 목표 삭제 (ARCHIVED 처리) |
| GET | `/{goalId}/detail`                   | 목표 상세·달성률 |
| GET | `/{goalId}/simulations/monthly-saving` | 월 저축액 시뮬레이션 |
| GET | `/market-trend`                      | 시세 변동 배너 |
| GET | `/summary`                           | 목표 요약 |

&nbsp;

### 비교 (`/api/v1/comparison`)

| 메서드 | 경로 | 설명 |
| --- | - | --- |
| GET |  | 종합 비교 |
| GET | `/assets` | 자산 비교 |
| GET | `/goals` | 목표 비교 |

&nbsp;

### 매물 (`/api/v1/properties`)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/median` | 지역·주택 유형별 전월세 중위가 |

---

## 응답 포맷

```json
// 성공
{ "success": true, "data": { ... }, "error": null }

// 실패
{ "success": false, "data": null, "error": { "code": "MEMBER_001", "message": "회원을 찾을 수 없습니다." } }
```

---

## 외부 연동

| 서비스 | 용도                                 |
| --- |------------------------------------|
| CODEF | 은행, 증권, 카드 계좌 조회 및 Connected ID 관리 |
| MOLIT | 전월세 실거래 수집 (아파트·연립·오피스텔·단독)        |
| Kakao | OAuth2 로그인 및 사용자 정보 조회             |
| Slack Webhook | 배치 실패 알림                           |

CODEF는 `local` 프로필에서 Mock 클라이언트(`CodefMockClient`)로 대체됩니다.

---

## 배포

`develop` 브랜치에 push되면 GitHub Actions가 자동으로 WAR를 빌드하고 Docker Hub에 이미지를 푸시합니다.

```
develop push
  → ./mvnw clean package -DskipTests (JDK 17)
  → docker build (tomcat:9-jdk17-temurin 기반)
  → Docker Hub push (:latest, :{SHA})
```

### 로컬에서 Docker 이미지로 실행

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw clean package -DskipTests
docker build -t nagasseum-backend .

# DB_URL, REDIS_HOST는 host.docker.internal 로 변경 후 실행
docker run -d --name nagasseum-backend --env-file .env -p 8080:8080 nagasseum-backend
```
