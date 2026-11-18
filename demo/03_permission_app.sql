-- 계정: app
-- 권한 검증(백엔드 계정): 이력은 지울 수 없고 구조도 바꿀 수 없다. 업무상 지우는 테이블만 지울 수 있다.

-- 기대: 성공
SELECT COUNT(*) AS 수강신청 FROM enrollment;

-- 기대: 거부 DELETE command denied
-- 수강신청 이력 삭제 (취소는 상태값으로 남긴다)
DELETE FROM enrollment WHERE std_no = '20240001';

-- 기대: 거부 DELETE command denied
DELETE FROM grade WHERE std_no = '20240001';

-- 기대: 거부 INSERT command denied
-- 학과는 시드로만 넣는다
INSERT INTO department (dept_no, dept_nm) VALUES (999, '시연학부');

-- 기대: 거부 DROP command denied
DROP TABLE enrollment;

-- 기대: 성공
-- 장바구니 삭제는 업무상 허용
DELETE FROM course_cart WHERE std_no = '20260001' AND lec_no = 191;
