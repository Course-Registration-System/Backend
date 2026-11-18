-- 계정: root
-- 01_state_change.sql 되돌리기. 전부 처음으로 돌리려면 docker compose down -v 후 다시 올린다.

-- 기대: 성공
UPDATE lecture SET cap = enrl_cnt + 1 WHERE lec_no = 198;

-- 기대: 성공
UPDATE semester_maximum_credit SET max_crdt = 21 WHERE std_no = '20260001' AND sem_no = 11;

-- 기대: 성공
UPDATE academic_schedule SET end_dtm = '2027-02-11 17:00:00' WHERE schd_no = 68;

-- 기대: 성공
UPDATE user_account SET acct_stat = 'ACTIVE' WHERE usr_no = '20260002';
