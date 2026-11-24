-- GRD-01 항목 점수(중간·기말·과제·출석)는 0~100, 소수 셋째 자리까지 받는다(학교 성적 시스템과 같게). TINYINT 는 정수만 담아
-- 소수를 넣으면 반올림해 저장하므로(92.5 → 93) DECIMAL(6,3) 으로 바꾼다. 총점(tot_scr)은 소수점 아래를 버린 정수라 그대로 둔다.
-- 기존 정수 점수는 값이 바뀌지 않는다(99 → 99.000). 0~100 CHECK 는 V1 의 것을 그대로 쓴다. 적용된 V1 은 고치지 않는다(Flyway 체크섬).

ALTER TABLE grade
  MODIFY mid_scr DECIMAL(6,3) NULL COMMENT '중간점수',
  MODIFY fin_scr DECIMAL(6,3) NULL COMMENT '기말점수',
  MODIFY hw_scr DECIMAL(6,3) NULL COMMENT '과제점수',
  MODIFY att_scr DECIMAL(6,3) NULL COMMENT '출석점수';
