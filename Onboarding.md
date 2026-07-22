# 백엔드 개발 환경 세팅 가이드

처음 클론하고 개발을 시작하기까지의 전체 흐름입니다. **위에서부터 순서대로** 따라오세요.
막히면 각 단계의 "안 될 때" 항목을 먼저 확인하고, 그래도 안 되면 **에러 로그 전체를 캡처해서** 백엔드 리드에게 공유해 주세요.


---

## 0단계. 미리 설치할 것

| 프로그램 | 버전 | 확인 명령 | 비고 |
| --- | --- | --- | --- |
| JDK | **17** | `java -version` |  |
| Tomcat | **9.x** | 폴더명으로 확인 | ⚠️ 10 이상이면 404 발생 |
| Docker Desktop | 최신 | `docker -v` | 실행까지 되어 있어야 함 |
| IntelliJ IDEA | Ultimate | | Community는 Tomcat 연동 불가 |
| Git | 최신 | `git --version` | |

### ⚠️ JDK 17이 아니면 반드시 맞출 것

```bash
java -version     # "17.x" 가 아니면 아래 진행

# macOS
brew install --cask temurin@17
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc

# 확인
java -version     # 17.x 여야 함
```

JDK 23 이상에서 빌드하면 `com.sun.tools.javac.code.TypeTag :: UNKNOWN` 에러가 납니다 (Lombok 호환 문제).

### ⚠️ Tomcat은 반드시 9.x

우리는 Spring Legacy(Boot 아님)를 쓰는데, Spring 5.3은 `javax.servlet` 기반입니다.
Tomcat 10부터는 `jakarta.servlet`으로 바뀌어서 **서블릿을 못 찾아 404가 납니다.**

[Apache Tomcat 9 다운로드](https://tomcat.apache.org/download-90.cgi) → Core > zip 받아서 압축 해제. 설치 불필요, 폴더만 있으면 됩니다.

> Maven은 설치하지 않아도 됩니다. IntelliJ에 내장되어 있고, 터미널에서는 `./mvnw`를 쓰면 됩니다.

---

## 1단계. 클론

```bash
git clone https://github.com/kb-final/nagasseum-cheongnyeon.git
cd nagasseum-cheongnyeon
git checkout develop
```

---

## 2단계. 환경변수 파일 생성

```bash
cp .env.example .env
```

로컬 개발은 기본값(localhost, root/1234)으로 그대로 동작합니다. 수정할 필요 없습니다.

> `.env`는 git에 올라가지 않습니다(.gitignore 등록됨). 외부 API 키가 발급되면 이 파일에 각자 채웁니다.

---

## 3단계. DB & Redis 실행

```bash
docker compose up -d
docker compose ps           # mysql, redis 컨테이너가 2개 다 올라왔는지 확인
```

### 스키마 생성 확인 (중요)

```bash
docker exec -it independence-mysql mysql -uroot -p1234 independence -e "SHOW TABLES;"
```

**16개 테이블**이 나와야 합니다.

**안 나올 때** — 이미 볼륨이 있으면 초기화 스크립트가 실행되지 않습니다. 볼륨까지 지우고 다시 올리세요.

```bash
docker compose down -v      # -v 는 볼륨(데이터)까지 삭제
docker compose up -d
```

---

## 4단계. 빌드

```bash
./mvnw clean package
```

`target/independence-backend.war` 가 생기면 성공입니다.

**안 될 때**
- `TypeTag :: UNKNOWN` → JDK 버전 문제. 0단계로 돌아가 JDK 17 확인
- `mvnw: Permission denied` → `chmod +x mvnw` 후 재시도

---

## 5단계. IntelliJ 설정

### 5-1. Project SDK 확인

**File → Project Structure → Project**
- SDK: **17**
- Language level: **17**

### 5-2. Tomcat 실행 구성 만들기

**Run → Edit Configurations → `+` → Tomcat Server → Local**

**Server 탭**

| 항목 | 값                                         |
| --- |-------------------------------------------|
| Application server | `Configure...` → Tomcat 9 압축 푼 폴더 지정      |
| VM options | `-Dspring.profiles.active=local,api` 붙여넣기 |
| HTTP port | 8080                                      |

**Deployment 탭**
1. `+` → **Artifact** → `independence-backend:war exploded` 선택
2. 아래 **Application context** 를 **`/`** 로 변경

> Application context를 안 바꾸면 주소가
> `http://localhost:8080/independence_backend_war_exploded/api/...` 처럼 길어집니다.
> 프론트 연동 시에도 혼란스러우니 `/`로 통일합니다.

### 5-3. 실행

초록색 ▶ 버튼 클릭. 콘솔에 아래가 보이면 성공입니다.

```
HikariPool-1 - Added connection      ← DB 연결 성공
Root WebApplicationContext initialized
DispatcherServlet - Completed initialization
Artifact is deployed successfully
```

---

## 6단계. 동작 확인

### ① DB 관통 확인

먼저 테스트 데이터를 넣습니다. **`--default-character-set=utf8mb4` 를 꼭 붙이세요** (안 붙이면 한글이 깨져 저장됩니다).

```bash
docker exec -i independence-mysql mysql -uroot -p1234 --default-character-set=utf8mb4 independence << 'SQL'
INSERT INTO member (kakao_id, nickname, birth_date, income_bracket)
VALUES ('test_001', '테스트유저', '1998-05-20', 'INCOME_100_120');
SELECT id, nickname FROM member;
SQL
```

**출력된 실제 id** 로 호출합니다 (1이 아닐 수 있습니다).

```bash
curl http://localhost:8080/api/v1/members/1
```
```json
{"success":true,"data":{"id":1,"nickname":"테스트유저","incomeBracket":"INCOME_100_120"},"error":null}
```

### ② 예외 처리 확인

```bash
curl http://localhost:8080/api/v1/members/999
```
```json
{"success":false,"data":null,"error":{"code":"MEMBER_001","message":"회원을 찾을 수 없습니다."}}
```



