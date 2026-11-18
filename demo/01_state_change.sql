-- 계정: root
-- 시연 중 데이터 상태 바꾸기. 기준 시드(T0) 위에서 실행한다. 되돌리기는 01_state_change_undo.sql
-- 각 문장 위의 "기대" 줄은 DemoScriptTest 가 확인한다(성공 = 바뀐 행이 1개 이상).

-- 기대: 성공
-- [정원] 한 자리 남은 198번 강의를 마감시킨다 → 학생 화면에서 신청이 막힌다
UPDATE lecture SET cap = enrl_cnt WHERE lec_no = 198;

-- 기대: 성공
SELECT lec_no, cap, enrl_cnt, cap - enrl_cnt AS 잔여 FROM lecture WHERE lec_no = 198;

-- 기대: 성공
-- [기간] 2학년 수강신청을 10:30에 끝난 것으로 바꾼다 → T1(11:00) 시점에 2학년 신청이 막힌다
UPDATE academic_schedule SET end_dtm = '2027-02-11 10:30:00' WHERE schd_no = 68;

-- 기대: 성공
-- [계정] 학생 계정을 비활성화한다 → 로그인이 막힌다
UPDATE user_account SET acct_stat = 'INACTIVE' WHERE usr_no = '20260001';

-- 기대: 성공
-- [성적] 제출된 2026 겨울 세계사(186번)를 관리자가 확정한 상태로 만든다 → 교수 수정이 막힌다
UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = '9001', cnfm_dtm = '2027-02-10 18:00:00' WHERE lec_no = 186;
