-- GRD-01 재정의(원점수만 입력, 제출 때 자동 계산).
--
-- 1. 최종점수(fnl_scr) 칸을 더한다. tot_scr 는 지금처럼 원점수 총점(항목 원점수 × 반영 비율 ÷ 100, 소수점 버림)이고, 등급은
--    최종점수로 정한다. 상대평가는 원점수 총점 순위로 구간에 맞춰 최종점수를 올리거나 내리고, 절대평가·P/F 는 최종점수 = 원점수 총점이다.
--    제출할 때만 채우고 원점수를 다시 저장하면 비운다. 기존 시드 행은 비어 있다(등급은 원점수 총점 기준으로 이미 들어 있음).
-- 2. P/F 과목도 원점수를 입력하고 원점수 총점 90 이상이면 P 이므로, P 인 행에도 점수가 있다. V1 의 "P 면 점수 없음" 제약을 지운다.

ALTER TABLE grade
  ADD COLUMN fnl_scr TINYINT NULL COMMENT '최종점수' AFTER tot_scr,
  ADD CONSTRAINT ck_grade_fnl_scr CHECK (fnl_scr BETWEEN 0 AND 100),
  DROP CHECK ck_grade_pf_no_score;
