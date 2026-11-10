# Backend

수강신청 및 성적 관리 시스템 Backend

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1 (Web MVC, JDBC, Validation) |
| DB 접근 | JdbcTemplate + 직접 작성한 SQL (ORM 사용 안 함) |
| 빌드 | Gradle (wrapper 포함, 별도 설치 불필요) |
| DB | MySQL 8.4 (테스트는 H2 인메모리) |

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

테이블은 자동 생성되지 않습니다. DDL 은 Docs 레포의 물리적 데이터 모델을 기준으로 SQL 파일로 관리하고, MySQL 에 직접 실행합니다.

## 테스트

```bash
./gradlew test
```

MySQL 없이 H2로 실행됩니다 (`src/test/resources/application.yml`).

## 하지 않는 것

- Lombok, JPA 등 ORM 은 쓰지 않습니다. SQL 을 직접 작성하는 것이 과목 산출물과 맞기 때문입니다.

## 패키지 구조 (예정)

```
com.crs.backend
├─ common      공통 (예외 처리, 응답 포맷, 헬스체크)
├─ auth        로그인
├─ course      강의
├─ enrollment  수강신청·장바구니·시간표
├─ grade       성적
└─ admin       학기·기간·사용자 관리
```

## 규칙

팀 규칙은 [Docs/TEAM_RULES.md](https://github.com/Course-Registration-System/Docs/blob/main/TEAM_RULES.md) 를 따릅니다. main 은 PR 로만 병합됩니다.
