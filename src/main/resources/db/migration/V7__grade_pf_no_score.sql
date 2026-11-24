-- GRD-01 P/F 과목은 점수 없이 P 또는 F 만 입력한다. V5 가 지운 "P 면 점수 없음" 제약을 V1 과 같은 식으로 되돌린다.
-- V5 이후 앱이 P/F 과목에 원점수를 저장했을 수 있으므로, 제약을 걸기 전에 P/F 과목 성적의 점수 칸을 비운다(등급은 그대로).

UPDATE grade g
  JOIN lecture l ON l.lec_no = g.lec_no
  JOIN course c ON c.crs_cd = l.crs_cd
   SET g.mid_scr = NULL, g.fin_scr = NULL, g.hw_scr = NULL, g.att_scr = NULL, g.tot_scr = NULL, g.fnl_scr = NULL
 WHERE c.eval_type = 'PASS_FAIL';

ALTER TABLE grade
  ADD CONSTRAINT ck_grade_pf_no_score CHECK (ltr_grd <> 'P' OR (mid_scr IS NULL AND fin_scr IS NULL AND hw_scr IS NULL AND att_scr IS NULL AND tot_scr IS NULL));
