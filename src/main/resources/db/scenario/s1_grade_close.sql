-- s1 성적 마감(2026-12-23 ~ 2027-01-17): 2026-2 확정, 겨울학기 성적 확정, 진급·졸업·복학, 2027 신입생 계정, 2027-1 최대학점. tools/seed/build_seed.py 로 생성한다.

-- 앞 단계까지 적용한 뒤 한 번 적용한다. 시연 중 앱 기능으로 이미 처리한 내용(입력한 점수, 계산한 최대학점, 만든 계정)은 덮어쓰지 않는다.

SET NAMES utf8mb4;



UPDATE grade SET mid_scr = 91, fin_scr = 91, hw_scr = 91, att_scr = 91, tot_scr = 91, ltr_grd = 'A-' WHERE std_no = '20260001' AND lec_no = 182 AND ltr_grd IS NULL;

INSERT INTO grade (std_no, lec_no, mid_scr, fin_scr, hw_scr, att_scr, tot_scr, ltr_grd) VALUES
  ('20240001', 158, 91, 91, 91, 91, 91, 'A-'),
  ('20240003', 158, 89, 71, 80, 99, 81, 'B-'),
  ('20240004', 158, 77, 77, 87, 96, 80, 'B-')
ON DUPLICATE KEY UPDATE ltr_grd = ltr_grd;

UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = '9001', cnfm_dtm = '2026-12-28 10:00:00' WHERE sem_no = 9 AND grd_stat <> 'CONFIRMED';

INSERT INTO grade (std_no, lec_no, mid_scr, fin_scr, hw_scr, att_scr, tot_scr, ltr_grd) VALUES
  ('20230003', 183, 81, 77, 80, 100, 81, 'B-'),
  ('20260004', 183, 75, 79, 83, 92, 79, 'C+'),
  ('20260001', 184, 77, 96, 89, 97, 87, 'B+'),
  ('20260003', 184, 89, 75, 92, 95, 84, 'B0'),
  ('20250002', 185, 84, 90, 91, 92, 87, 'B+')
ON DUPLICATE KEY UPDATE ltr_grd = ltr_grd;

UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = '9001', cnfm_dtm = '2027-01-16 17:00:00' WHERE sem_no = 10 AND grd_stat <> 'CONFIRMED';

UPDATE student SET yr_lvl = 4, std_stat = 'GRADUATED' WHERE std_no = '20230001';

UPDATE student SET yr_lvl = 4, std_stat = 'ATTENDING' WHERE std_no = '20240001';

UPDATE student SET yr_lvl = 4, std_stat = 'ATTENDING' WHERE std_no = '20240002';

UPDATE student SET yr_lvl = 4, std_stat = 'ATTENDING' WHERE std_no = '20240003';

UPDATE student SET yr_lvl = 4, std_stat = 'ATTENDING' WHERE std_no = '20240004';

UPDATE student SET yr_lvl = 4, std_stat = 'ATTENDING' WHERE std_no = '20240005';

UPDATE student SET yr_lvl = 3, std_stat = 'ATTENDING' WHERE std_no = '20250001';

UPDATE student SET yr_lvl = 2, std_stat = 'ATTENDING' WHERE std_no = '20250002';

UPDATE student SET yr_lvl = 3, std_stat = 'ATTENDING' WHERE std_no = '20250003';

UPDATE student SET yr_lvl = 2, std_stat = 'ATTENDING' WHERE std_no = '20260001';

UPDATE student SET yr_lvl = 2, std_stat = 'ATTENDING' WHERE std_no = '20260002';

UPDATE student SET yr_lvl = 2, std_stat = 'ATTENDING' WHERE std_no = '20260003';

UPDATE student SET yr_lvl = 2, std_stat = 'ATTENDING' WHERE std_no = '20260004';

INSERT INTO user_account (usr_no, pwd, usr_nm, email, role, acct_stat) VALUES
  ('20270001', '$2a$10$mVtkuC8t.ybJShFMmg7dAePnho.f1IpD92TXrTXipF.OhtbWdF/.S', '문서윤', '20270001@example.ac.kr', 'STUDENT', 'ACTIVE'),
  ('20270002', '$2a$10$mVtkuC8t.ybJShFMmg7dAePnho.f1IpD92TXrTXipF.OhtbWdF/.S', '류채원', '20270002@example.ac.kr', 'STUDENT', 'ACTIVE')
ON DUPLICATE KEY UPDATE usr_no = usr_no;

INSERT INTO student (std_no, dept_no, yr_lvl, std_stat) VALUES
  ('20270001', 101, 1, 'ATTENDING'),
  ('20270002', 102, 1, 'ATTENDING')
ON DUPLICATE KEY UPDATE std_no = std_no;

INSERT INTO semester_maximum_credit (std_no, sem_no, max_crdt) VALUES
  ('20230002', 11, 18),
  ('20240001', 11, 21),
  ('20240002', 11, 18),
  ('20240003', 11, 18),
  ('20240004', 11, 18),
  ('20240005', 11, 18),
  ('20250001', 11, 18),
  ('20250002', 11, 21),
  ('20250003', 11, 18),
  ('20260001', 11, 21),
  ('20260002', 11, 18),
  ('20260003', 11, 18),
  ('20260004', 11, 18),
  ('20270001', 11, 18),
  ('20270002', 11, 18)
ON DUPLICATE KEY UPDATE max_crdt = max_crdt;
