-- GRD-01 재정의: P/F 과목도 교수·조교가 원점수를 입력하고, 반영 비율로 낸 총점이 90 이상이면 P 로 판정한다. 그래서 P 인 행에도
-- 항목 점수·총점이 있다. V1 의 "P 면 점수가 없다" 제약을 지운다. 기존 시드의 P 행(점수 없음)은 그대로 둔다.

ALTER TABLE grade DROP CHECK ck_grade_pf_no_score;
