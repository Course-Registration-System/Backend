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
| 스키마 관리 | Flyway (적용 예정) |
| 테스트 DB | Testcontainers MySQL 8.4 (적용 예정) |

## 실행 방법

1. JDK 21 설치 (`java -version` 으로 확인)
2. MySQL 준비 — Docker가 있으면 아래 한 줄이면 됩니다.
   ```bash
   docker compose up -d
   ```
   직접 설치한 MySQL을 쓰려면 `crs` 데이터베이스와 `crs` / `crs` 계정을 만들고, `.env.example` 을 복사한 `.env` 에서 값을 맞춥니다.
3. 서버 실행
   ```bash
   ./gradlew bootRun        # Windows: gradlew.bat bootRun
   ```
4. 확인: http://localhost:8080/api/health → `{"status":"ok", ...}`

테이블은 Flyway가 서버 시작 시 만듭니다(적용 예정). DDL 은 Docs 레포의 물리적 데이터 모델(`schema.sql`)을 기준으로 `src/main/resources/db/migration/` 에 `V1__init_schema.sql` 로 두고, 이후 변경은 `V2__설명.sql` 처럼 새 파일로 쌓습니다. 적용된 파일은 고치지 않습니다.

적용 전까지는 지금처럼 DDL 을 MySQL 에 직접 실행합니다.

## 코드 포맷

```bash
./gradlew spotlessApply   # 전체 파일을 규칙에 맞게 고침
```

`./gradlew build` 에 포맷 검사가 포함되어 있어, 포맷이 어긋나면 CI 가 실패합니다. PR 올리기 전에 한 번 실행하세요. IntelliJ 에서는 Settings → Plugins 에서 "palantir-java-format" 을 설치하면 저장할 때 같은 규칙이 적용됩니다.

## 테스트

```bash
./gradlew test
```

Testcontainers 로 MySQL 8.4 컨테이너를 띄워 실행합니다(적용 예정). Docker 가 켜져 있어야 합니다. 스키마가 MySQL 전용 문법(함수 인덱스, CHECK 제약)을 써서 H2 에서는 테이블을 만들 수 없기 때문입니다.

적용 전까지는 H2 로 실행됩니다 (`src/test/resources/application.yml`).

## 하지 않는 것

- Lombok, JPA 등 ORM 은 쓰지 않습니다. SQL 을 직접 작성하는 것이 과목 산출물과 맞기 때문입니다.

## 패키지 구조

```
com.crs.backend
├─ common      공통 (예외 처리, 응답 포맷, 헬스체크, 현재 시각)
├─ auth        로그인, 로그인 사용자 정보 (USR-01)
├─ user        계정 등록·수정·비활성화 (USR-02)
├─ academic    학기·학사일정, 최대학점 산정, 대시보드 (ACA-01·04)
├─ course      과목, 선수과목 (ACA-02)
├─ lecture     강의 개설·검색, 담당 강의, 수강생 명단, 강의계획서, 조교 (ACA-03, REG-01, LEC-01~04)
├─ enrollment  장바구니, 수강신청·취소, 신청 현황, 시간표 (REG-02~05)
└─ grade       성적 입력·확정·조회 (GRD-01~03)
```

- 패키지는 업무 영역으로 나눕니다. 이름은 용어사전을 따릅니다: `course` 는 **과목**(교육과정 단위), `lecture` 는 **강의**(학기·분반으로 개설된 과목)입니다.
- 관리자 기능은 따로 모으지 않고 해당 업무 패키지에 둡니다. 관리자 전용 API 는 `/api/admin/**` 경로와 권한 검사로 구분합니다.
- 괄호 안은 Docs 레포 업무분석서의 단위업무 번호입니다.

## 규칙

팀 규칙은 [Docs/TEAM_RULES.md](https://github.com/Course-Registration-System/Docs/blob/main/TEAM_RULES.md) 를 따릅니다. main 은 PR 로만 병합됩니다.
