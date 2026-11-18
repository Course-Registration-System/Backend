# Backend

수강신청 및 성적 관리 시스템 Backend

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1 (Web MVC, JDBC, Validation) |
| DB 접근 | JdbcTemplate + 직접 작성한 SQL (ORM 사용 안 함) |
| 포맷터 | Spotless (Palantir Java Format) |
| 빌드 | Gradle (wrapper 포함, 별도 설치 불필요) |
| DB | MySQL 8.4 |
| 스키마 관리 | Flyway |
| 테스트 DB | Testcontainers MySQL 8.4 |

## 실행 방법

1. JDK 21, Docker 설치 (`java -version`, `docker version` 으로 확인)
2. `.env.example` 을 `.env` 로 복사하고 비밀번호 4개를 채웁니다. 로컬에서는 아무 값이나 괜찮습니다. `.env` 는 커밋하지 않습니다.
3. MySQL 실행
   ```bash
   docker compose up -d
   ```
   첫 기동 때 `docker/mysql/initdb/01-users.sh` 가 DB 계정을 만듭니다. 3306 포트를 이미 쓰고 있으면 `.env` 의 `DB_PORT` 를 바꿉니다.
4. 서버 실행
   ```bash
   ./gradlew bootRun        # Windows: gradlew.bat bootRun
   ```
   시작할 때 Flyway 가 테이블과 권한을 적용합니다.
5. 확인: http://localhost:8080/api/health → `{"status":"ok", ...}`

DB 를 처음 상태로 되돌리려면 `docker compose down -v` 후 3번부터 다시 합니다(데이터가 모두 지워집니다).

### 설정값

`docker compose` 와 백엔드가 같은 `.env` 를 읽습니다. 백엔드는 `application.yml` 의 `spring.config.import: optional:file:.env[.properties]` 로 읽고, 파일이 없으면 환경 변수를 씁니다.

### DB 계정

| 계정 | 쓰는 곳 | 권한 |
| --- | --- | --- |
| `migrator` | Flyway (서버 시작 시) | `crs` 의 DDL·GRANT. 런타임에는 쓰지 않음 |
| `app` | 백엔드 런타임 | 조회 전부, 테이블별 추가·수정, 업무상 행을 지우는 5개 테이블만 삭제. DDL 없음 |
| `readonly` | 실험·분석 스크립트 | 조회만 |
| `root` | 계정 생성, 시연 중 데이터 제어 | 전부 |

`app` 권한은 `R__grants.sql` 에 있습니다. 권한 밖의 SQL(예: 이력 테이블 DELETE)은 DB 가 거부합니다.

### 스키마 변경

DDL 은 Docs 레포의 물리적 데이터 모델(`schema.sql`, `grants.sql`)이 기준입니다. `src/main/resources/db/migration/` 에 아래처럼 둡니다.

- `V1__init_schema.sql`: 기준 시점의 `schema.sql`. 적용된 `V` 파일은 고치지 않고, 변경은 `V2__설명.sql` 처럼 새 파일로 쌓습니다.
- `R__grants.sql`: `grants.sql`. 바뀌면 Flyway 가 다시 적용합니다. 데이터베이스 이름 `crs` 를 가정합니다.

## 시드 데이터

서버가 시작할 때 Flyway 가 시드(`db/seed/R__seed_dev.sql`)도 함께 넣습니다. 개발·테스트·시연 모두 이 데이터를 씁니다.

- 모듈별로 확인할 사례, 로그인 계정, 시점은 [docs/seed-data.md](docs/seed-data.md) 에 있습니다. 모든 계정의 비밀번호는 `test1234!` 입니다.
- 앱의 현재 시각은 `.env` 의 `CLOCK_FIXED_AT` 으로 고정합니다(기본 T0 = 2027-02-10 18:00). 현재 시각이 필요한 코드는 `Clock` 빈을 주입받아 씁니다. `LocalDateTime.now()` 를 직접 부르거나 SQL 에서 `NOW()` 를 쓰지 않습니다.
- 시드는 `tools/seed/build_seed.py` 가 만듭니다(Python 3, 표준 라이브러리만). 생성물을 직접 고치지 말고 생성기를 고친 뒤 `python -X utf8 tools/seed/build_seed.py` 로 다시 만듭니다. `SeedDataTest` 가 시드의 업무 규칙 위반을 검사합니다.
- 시연용 데이터 제어 SQL 은 [demo/](demo/README.md) 에 있습니다.

## 코드 포맷

```bash
./gradlew spotlessApply   # 전체 파일을 규칙에 맞게 고침
```

`./gradlew build` 에 포맷 검사가 포함되어 있어, 포맷이 어긋나면 CI 가 실패합니다. PR 올리기 전에 한 번 실행하세요. IntelliJ 에서는 Settings → Plugins 에서 "palantir-java-format" 을 설치하면 저장할 때 같은 규칙이 적용됩니다.

## 테스트

```bash
./gradlew test
```

Testcontainers 로 MySQL 8.4 컨테이너를 띄워 실행합니다. Docker 가 켜져 있어야 합니다. 스키마가 MySQL 전용 문법(함수 인덱스, CHECK 제약)을 써서 H2 에서는 테이블을 만들 수 없기 때문입니다.

- DB 가 필요한 테스트는 `support.IntegrationTest` 를 상속합니다. 컨테이너 하나를 테스트 실행 전체에서 같이 씁니다.
- 운영과 같은 순서로 준비합니다(계정 생성 → Flyway). 테스트도 `app` 계정으로 접속하므로 권한 밖의 SQL 은 테스트에서도 실패합니다.

## 하지 않는 것

- Lombok, JPA 등 ORM 은 쓰지 않습니다. SQL 을 직접 작성하는 것이 과목 산출물과 맞기 때문입니다.

## 패키지 구조

```
com.crs.backend
├─ common      공통 (예외 처리, 응답 포맷, 헬스체크, 현재 시각)
│  └─ auth     토큰 검증, 로그인 사용자 정보(CurrentUser), 권한 검사
├─ user        로그인·토큰 발급, 계정 등록·수정·비활성화 (USR-01·02)
├─ academic    학기·학사일정, 최대학점 산정, 대시보드 (ACA-01·04)
├─ course      과목, 선수과목 (ACA-02)
├─ lecture     강의 개설·검색, 담당 강의, 수강생 명단, 강의계획서, 조교 (ACA-03, REG-01, LEC-01~04)
├─ enrollment  장바구니, 수강신청·취소, 신청 현황, 시간표 (REG-02~05)
└─ grade       성적 입력·확정·조회 (GRD-01~03)
```

- 패키지는 업무 영역으로 나눕니다. 이름은 용어사전을 따릅니다: `course` 는 **과목**(교육과정 단위), `lecture` 는 **강의**(학기·분반으로 개설된 과목)입니다.
- `common.auth` 는 토큰만 보고 DB 를 읽지 않습니다. 로그인처럼 `user_account` 를 읽는 업무는 `user` 에 둡니다. 공통 코드가 업무 패키지에 의존하지 않게 하기 위해서입니다.
- 관리자 기능은 따로 모으지 않고 해당 업무 패키지에 둡니다. 관리자 전용 API 는 `/api/admin/**` 경로와 권한 검사로 구분합니다.
- 괄호 안은 Docs 레포 업무분석서의 단위업무 번호입니다.

## 패키지 안 구성

업무 패키지마다 아래 이름을 씁니다. `enrollment` 를 예로 듭니다.

```
enrollment
├─ EnrollmentController   HTTP 요청·응답만 다룸
├─ EnrollmentService      업무 규칙과 트랜잭션(@Transactional)
├─ EnrollmentRepository   SQL (JdbcTemplate / NamedParameterJdbcTemplate)
├─ Enrollment             테이블 한 행 (record)
└─ dto
   ├─ EnrollmentRequest   요청 본문 (record + Bean Validation)
   └─ EnrollmentResponse  응답 본문 (record)
```

- 호출 방향은 Controller → Service → Repository 한 방향입니다. Controller 에서 Repository 를 바로 부르지 않습니다.
- SQL 은 Repository 에만 씁니다.
- 다른 모듈의 테이블은 JOIN 으로 읽기만 합니다. 쓰기가 필요하면 그 테이블을 맡은 모듈의 Service 를 부릅니다.
- 클래스 이름은 용어사전의 물리명을 단수형으로 씁니다(`lecture` → `Lecture`, `course_cart` → `CourseCart`).

## 오류 응답

오류는 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) 형식의 Spring `ProblemDetail`(`application/problem+json`)로 돌려줍니다. 성공 응답은 감싸지 않고 결과를 그대로 돌려줍니다.

- 업무 규칙 위반은 `common` 의 `BusinessException(ErrorCode)` 로 던지고, `common` 의 `@RestControllerAdvice` 가 `ProblemDetail` 로 바꿉니다.
- `ErrorCode` 는 HTTP 상태와 영문 대문자 코드를 함께 가집니다. 프론트는 `code` 로 경우를 구분합니다.

| 상태 | 쓰는 경우 | `code` 예 |
| --- | --- | --- |
| 400 | 입력 형식 오류(Bean Validation 실패) | `INVALID_INPUT` |
| 401 | 로그인하지 않음, 토큰 만료 | `UNAUTHENTICATED` |
| 403 | 권한 없음(학생이 관리자 API 호출 등) | `FORBIDDEN` |
| 404 | 대상 없음 | `LECTURE_NOT_FOUND` |
| 409 | 업무 규칙 위반(정원 초과, 시간 겹침, 기간 아님 등) | `LECTURE_FULL`, `TIME_CONFLICT` |

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "정원이 마감된 강의입니다.",
  "instance": "/api/enrollments",
  "code": "LECTURE_FULL"
}
```

## 규칙

팀 규칙은 [Docs/TEAM_RULES.md](https://github.com/Course-Registration-System/Docs/blob/main/TEAM_RULES.md) 를 따릅니다. main 은 PR 로만 병합됩니다.
