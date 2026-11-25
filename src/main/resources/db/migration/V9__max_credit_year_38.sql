-- 최대학점 규칙을 학년 38학점 기준으로 바꾼다(ACA-01, #51).
-- 1학년은 첫 학기 22, 두 번째 학기 38 − 첫 학기 수강 학점(상한 22). 2학년 이상은 첫 학기 19, 두 번째 학기 19 + 남긴 학점,
-- 평점 4.0 이상이면 +3, 학기 상한 24. 최대학점이라 하한은 두지 않는다. 계절학기 3·6과 이미 저장된 과거 학기의 18·21도 이 범위 안이다.
ALTER TABLE semester_maximum_credit
  DROP CHECK ck_semester_maximum_credit_max_crdt_value;
ALTER TABLE semester_maximum_credit
  ADD CONSTRAINT ck_semester_maximum_credit_max_crdt_value CHECK (max_crdt BETWEEN 0 AND 24);

-- 최대학점 제외 과목(한반도와평화통일 등). 졸업 학점에는 들어가지만 최대학점과 비교하는 신청 학점 합계에서만 뺀다.
-- 채플은 이수구분(CHAPEL)으로 따로 빼므로 여기 표시하지 않는다
ALTER TABLE course
  ADD COLUMN max_crdt_exmp_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '최대학점제외여부: Y(예), N(아니오)',
  ADD CONSTRAINT ck_course_max_crdt_exmp_yn CHECK (max_crdt_exmp_yn IN ('Y', 'N'));
