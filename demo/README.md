# 시연용 데이터 제어 SQL

시연 중 데이터 상태 변경, 예외 상황(제약 위반 거부), 권한 검증(계정별 거부)을 바로 보여 주는 SQL 이다. 모두 **T1(2027-02-11 10:05, 오버레이 s1·s2 적용 후)** 상태를 기준으로 쓴다. 시연 흐름의 ⑦ 단계다(docs/seed-data.md). s3 를 적용하면 일부 문장(현재 학기 둘 등)이 다르게 동작한다.

| 파일 | 접속 계정 | 보여 주는 것 |
| --- | --- | --- |
| `01_state_change.sql` | root | 정원 마감, 최대학점 변경, 신청 기간 종료, 계정 비활성화 |
| `01_state_change_undo.sql` | root | 01 되돌리기 |
| `02_constraint_violations.sql` | app | 중복 신청, 현재 학기 둘, 점수 범위, 없는 등급, 학점, 반영비율 합, 온라인 강의실, 없는 강의실, 최대학점 값, 기간 역전 → 모두 DB 제약으로 거부 |
| `03_permission_app.sql` | app | 이력 삭제·DDL·시드 전용 테이블 추가 거부, 장바구니 삭제는 허용 |
| `04_permission_readonly.sql` | readonly | 조회만 허용 |

## 실행

파일 전체를 실행할 때는 `--force` 로 오류가 나도 다음 문장으로 넘어가게 한다. 시연에서는 문장을 하나씩 복사해 실행해도 된다.

```bash
docker exec -i crs-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t crs' < demo/01_state_change.sql
docker exec -i crs-mysql sh -c 'mysql -uapp -p"$APP_DB_PASSWORD" --force crs' < demo/02_constraint_violations.sql
docker exec -i crs-mysql sh -c 'mysql -uapp -p"$APP_DB_PASSWORD" --force -t crs' < demo/03_permission_app.sql
docker exec -i crs-mysql sh -c 'mysql -ureadonly -p"$READONLY_DB_PASSWORD" --force -t crs' < demo/04_permission_readonly.sql
```

## 기대 결과 표기

각 문장 위의 `-- 기대: 성공` 또는 `-- 기대: 거부 <오류에 들어갈 문구>` 를 `DemoScriptTest` 가 매 빌드마다 확인한다. 시드를 바꿔 번호가 달라지면 테스트가 실패하므로 이 파일도 같이 고친다. 시연 장면 순서가 정해지면 파일 안의 문장 순서를 장면 순서에 맞춘다.
