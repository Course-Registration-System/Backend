-- 행 하나만 보고 판단할 수 있는 업무 규칙을 CHECK 로 더한다. 응용에서 이미 지키는 규칙이라 기존 데이터는 모두 통과해야 한다.
-- 물리모델 schema.sql 에 있으나 V1 에 빠진 제약이다. 대상학년 제약(ACA-01)은 V2 로 먼저 더했다.

-- 신청인원은 정원을 넘지 않는다. 관리자 추가(REG-05)는 정원이 차 있으면 정원을 1 늘린 뒤 넣는다
ALTER TABLE lecture
  ADD CONSTRAINT ck_lecture_enrl_cnt_cap CHECK (enrl_cnt <= cap),
-- 폐강하면 신청을 모두 취소하므로 신청인원은 0이다(ACA-03)
  ADD CONSTRAINT ck_lecture_closed_no_enrl CHECK (lec_stat <> 'CLOSED' OR enrl_cnt = 0),
-- 확정이면 확정자와 확정일시가 모두 있고, 아니면 둘 다 없다(GRD-02). 확정일시는 ck_lecture_confirm_time 이 묶는다
  ADD CONSTRAINT ck_lecture_confirm_emp CHECK ((grd_stat = 'CONFIRMED') = (cnfm_emp_no IS NOT NULL));

-- 채플은 0.5학점이며 P/F로 평가한다(ACA-02)
ALTER TABLE course
  ADD CONSTRAINT ck_course_chapel CHECK (cmpl_type <> 'CHAPEL' OR (eval_type = 'PASS_FAIL' AND crdt = 0.5));
