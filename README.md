# nagasseum-cheongnyeon (backend)

청년 독립 준비 플랫폼 백엔드 · **Spring Legacy 5.3 + MyBatis + JDK 17 + Maven**

> 처음 세팅 시 → **[ONBOARDING.md](./ONBOARDING.md)** 를 순서대로 따라오세요.

---

## ⚠️ 필수 환경

| 항목 | 버전 | 주의 |
| --- | --- | --- |
| JDK | **17** | 23 이상이면 빌드 실패 (Lombok 호환) |
| Tomcat | **9.x** | 10 이상이면 404 발생 (jakarta 네임스페이스) |
| Docker Desktop | 최신 | MySQL · Redis 실행용 |
| IntelliJ | Ultimate | Community는 Tomcat 연동 불가 |

Maven은 설치 불필요 (`./mvnw` 또는 IntelliJ 내장 사용)

---

## 빠른 시작

```bash
git clone https://github.com/kb-final/nagasseum-cheongnyeon.git
cd nagasseum-cheongnyeon
git checkout develop

cp .env.example .env          # 로컬은 기본값으로 동작
docker compose up -d          # MySQL + Redis (스키마 자동 생성)
./mvnw clean package          # → target/independence-backend.war
```

IntelliJ에서 **Tomcat Server(Local)** 실행 구성 생성 후:
- Server 탭 → VM options: `-Dspring.profiles.active=local,api`
- Deployment 탭 → `war exploded` 추가 → **Application context: `/`**

### 동작 확인

```bash
curl http://localhost:8080/api/v1/members/1           # DB 관통 확인
curl http://localhost:8080/api/v1/members/999         # MEMBER_001 에러 (정상)
```

---

## 프로필 (api / batch)

같은 WAR를 **실행 시 프로필만 달리해** 두 역할로 띄웁니다.

| 프로필 | 활성화 | 역할 |
| --- | --- | --- |
| `api` | Controller, ApiConfig | 사용자 요청 처리 |
| `batch` | Scheduler, BatchConfig(@EnableScheduling) | 새벽 배치 (매물·정책·자산 동기화) |

Service·Mapper는 양쪽 모두 로딩되므로, 자산 동기화 로직을 한 번만 작성하면
사용자 새로고침(api)과 새벽 배치(batch)가 같은 코드를 씁니다.

---

## 패키지 구조

```
com.team.independence
├── member/          회원·인증      
├── asset/           자산 진단·동기화 
├── goal/            목표·템플릿  
├── property/        실거래 매물
├── policy/          정책 추천 
├── compare/         또래 비교 
├── scheduler/       배치 (batch 프로필 전용)
├── external/        외부 API 클라이언트
├── common/          공통 인프라 
└── config/          설정     
```

각 도메인 내부는 동일한 구조:
```
{도메인}/
├── controller/   HTTP 요청·응답 (로직 없음)
├── service/      비즈니스 로직 (@Transactional)
├── mapper/       DB 접근 인터페이스 (@Mapper)
├── domain/       테이블 매핑 객체
└── dto/          요청·응답 전용 객체
```
Mapper XML은 `resources/mybatis/mapper/{도메인}/` 아래에 둡니다.

**`member` 도메인이 표준 패턴입니다. 그대로 복제해서 자기 도메인을 채우세요.**
