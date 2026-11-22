-- 수강신청 및 성적관리 시스템 DB 권한 (MySQL 8.4)
-- 계정은 users.sh가 환경 변수의 비밀번호로 먼저 만든다. 이 파일에는 권한만 두고 비밀번호는 두지 않는다.
-- Backend에서는 Flyway 반복 마이그레이션 R__grants.sql로 둔다. migrator 계정이 V1__init_schema.sql 다음에 적용하고,
-- 이 파일이 바뀌면 Flyway가 다시 적용한다. GRANT는 몇 번 실행해도 결과가 같다. (데이터베이스 이름 crs)
--
-- app      : 백엔드가 쓰는 계정. DDL 없음.
--            이력을 남겨야 하는 테이블(계정·과목·강의·수강신청·성적 등)은 지우지 않고 상태값으로 관리하므로 DELETE를 주지 않는다.
--            DELETE는 업무상 행을 지우는 여섯 테이블에만 준다.
--              course_cart                    장바구니 삭제 (REG-02), 폐강 시 장바구니 삭제 (ACA-03)
--              teaching_assistant_assignment  조교 삭제 (LEC-04)
--              lecture_time_slot              강의시간 칸 삭제 (ACA-03 수정)
--              prerequisite_course            선수과목 지정 해제 (ACA-02 수정)
--              academic_schedule              시작 전 일정 삭제 (ACA-01)
--              semester_maximum_credit        최대학점 다시 산정 시 그 학기 값 지우고 새로 넣기 (ACA-01)
--            학과·강의실·관리자는 시드로만 넣으므로 읽기만 준다.
-- readonly : 실험·분석 스크립트용. SELECT만.

GRANT SELECT ON crs.* TO 'app'@'%';

GRANT INSERT, UPDATE ON crs.user_account TO 'app'@'%';
GRANT INSERT, UPDATE ON crs.student TO 'app'@'%';
GRANT INSERT, UPDATE ON crs.professor TO 'app'@'%';
GRANT INSERT, UPDATE ON crs.semester TO 'app'@'%';
GRANT INSERT, UPDATE ON crs.course TO 'app'@'%';
GRANT INSERT, UPDATE ON crs.lecture TO 'app'@'%';
GRANT INSERT, UPDATE ON crs.enrollment TO 'app'@'%';
GRANT INSERT, UPDATE ON crs.grade TO 'app'@'%';

GRANT INSERT, UPDATE, DELETE ON crs.course_cart TO 'app'@'%';
GRANT INSERT, UPDATE, DELETE ON crs.teaching_assistant_assignment TO 'app'@'%';
GRANT INSERT, UPDATE, DELETE ON crs.lecture_time_slot TO 'app'@'%';
GRANT INSERT, UPDATE, DELETE ON crs.prerequisite_course TO 'app'@'%';
GRANT INSERT, UPDATE, DELETE ON crs.academic_schedule TO 'app'@'%';
GRANT INSERT, UPDATE, DELETE ON crs.semester_maximum_credit TO 'app'@'%';

GRANT SELECT ON crs.* TO 'readonly'@'%';
