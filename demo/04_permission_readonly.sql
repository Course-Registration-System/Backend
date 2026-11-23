-- 계정: readonly
-- 권한 검증(실험·분석 계정): 조회만 된다.

-- 기대: 성공
SELECT lec_no, cap, enrl_cnt FROM lecture WHERE lec_no = 190;

-- 기대: 거부 UPDATE command denied
UPDATE lecture SET cap = cap + 1 WHERE lec_no = 190;

-- 기대: 거부 INSERT command denied
INSERT INTO course_cart (std_no, lec_no, reg_dtm) VALUES ('20260001', 224, '2027-02-10 18:00:00');
