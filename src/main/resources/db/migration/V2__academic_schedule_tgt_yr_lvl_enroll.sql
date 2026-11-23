-- ACA-01 대상학년은 수강신청 일정에만 지정한다.
-- 물리모델(Docs docs/model/physical/schema.sql)에는 있었으나 V1 에 빠진 제약을 더한다. 서비스 검사(SemesterService)를 거치지 않는
-- 시드·직접 SQL 도 막는다. 적용된 V1 은 고치지 않는다(Flyway 체크섬).

ALTER TABLE academic_schedule
  ADD CONSTRAINT ck_academic_schedule_tgt_yr_lvl_enroll CHECK (tgt_yr_lvl IS NULL OR schd_type = 'ENROLL');
