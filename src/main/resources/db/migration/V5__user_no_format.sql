-- 사용자번호(로그인ID) 형식(USR-01·02). 숫자만 쓰고 자리수로 신분을 나눈다:
-- 관리자(정보화팀) 5자리, 교수 6자리, 학부생 8자리, 대학원생 10자리. 응용 검사는 UserNo.java 가 같은 규칙을 쓴다.
-- 대학원생은 학생 행으로 두지만 조교로만 쓴다. 장바구니·수강신청·최대학점 대상이 아니다.

-- 이미 쓰던 DB 에는 옛 시드 관리자 '9001'(4자리)이 남아 있어 아래 CHECK 를 추가할 수 없다. 시드(R__)는 이 파일 다음에 돌므로
-- 여기서 먼저 새 번호 '90001'로 옮긴다. 번호를 참조하는 곳은 관리자와 강의 확정자이고, FK 가 ON UPDATE RESTRICT 라
-- 시드와 같은 방식으로 FK 검사를 잠시 끄고 세 곳을 함께 바꾼다. '9001'이 없는 DB(새 DB)에서는 아무것도 바뀌지 않는다.
SET FOREIGN_KEY_CHECKS = 0;
UPDATE user_account SET usr_no = '90001' WHERE usr_no = '9001';
UPDATE administrator SET emp_no = '90001' WHERE emp_no = '9001';
UPDATE lecture SET cnfm_emp_no = '90001' WHERE cnfm_emp_no = '9001';
SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE user_account
  ADD CONSTRAINT ck_user_account_usr_no CHECK (
    (role = 'STUDENT' AND usr_no REGEXP '^([0-9]{8}|[0-9]{10})$')
    OR (role = 'PROFESSOR' AND usr_no REGEXP '^[0-9]{6}$')
    OR (role = 'ADMIN' AND usr_no REGEXP '^[0-9]{5}$'));

-- 대학원생(학번 10자리)은 장바구니에 담거나 신청하지 않고 최대학점도 받지 않는다. 성적은 신청(enrollment)을 참조하므로 따로 두지 않는다
ALTER TABLE course_cart
  ADD CONSTRAINT ck_course_cart_undergraduate CHECK (CHAR_LENGTH(std_no) <> 10);
ALTER TABLE enrollment
  ADD CONSTRAINT ck_enrollment_undergraduate CHECK (CHAR_LENGTH(std_no) <> 10);
ALTER TABLE semester_maximum_credit
  ADD CONSTRAINT ck_semester_maximum_credit_undergraduate CHECK (CHAR_LENGTH(std_no) <> 10);
