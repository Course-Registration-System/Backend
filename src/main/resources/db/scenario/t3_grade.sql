-- T3 시점(2027-06-20 10:00, 2027-1 성적 입력 기간)으로 옮기는 오버레이. tools/seed/build_seed.py 로 생성한다.

-- 기본 시드(R__seed_dev.sql) 위에 한 번만 적용한다. 되돌리려면 기본 시드부터 다시 넣는다.

-- T0 → T3 사이의 일: 2·1학년 수강신청(02-11·12), 2026 겨울학기 성적 확정(02-15), 현재학기 2027-1로 전환, 2027-1 성적 입력 시작

SET NAMES utf8mb4;



INSERT INTO enrollment (std_no, lec_no, enrl_stat, retake_yn, proc_type, enrl_dtm) VALUES
  ('20270001', 187, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:07:00'),
  ('20270001', 189, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:10:00'),
  ('20260001', 190, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:04:00'),
  ('20260003', 190, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:06:00'),
  ('20260001', 192, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:10:00'),
  ('20260003', 192, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:12:00'),
  ('20270002', 199, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:08:00'),
  ('20270002', 201, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:11:00'),
  ('20250002', 202, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:05:00'),
  ('20260002', 202, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:05:00'),
  ('20260004', 202, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:07:00'),
  ('20250002', 203, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:08:00'),
  ('20260002', 203, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:08:00'),
  ('20260004', 203, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:10:00'),
  ('20250002', 204, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:11:00'),
  ('20260002', 204, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:11:00'),
  ('20260004', 204, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:13:00'),
  ('20270001', 211, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:01:00'),
  ('20270002', 211, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:02:00'),
  ('20270001', 213, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:13:00'),
  ('20270002', 213, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:14:00'),
  ('20270001', 214, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:16:00'),
  ('20270002', 214, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:17:00'),
  ('20270002', 215, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:20:00'),
  ('20260004', 217, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:19:00'),
  ('20250002', 220, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:17:00'),
  ('20260002', 220, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:17:00'),
  ('20260003', 220, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:18:00'),
  ('20270001', 221, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:19:00'),
  ('20260001', 222, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:16:00'),
  ('20260002', 222, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:14:00'),
  ('20260003', 222, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:15:00'),
  ('20260004', 225, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:16:00'),
  ('20250002', 226, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:14:00'),
  ('20260001', 226, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:13:00'),
  ('20250002', 229, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:02:00'),
  ('20260001', 229, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:01:00'),
  ('20260002', 229, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:02:00'),
  ('20260003', 229, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:03:00'),
  ('20260004', 229, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:04:00'),
  ('20270001', 229, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:04:00'),
  ('20270002', 229, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:05:00');

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 187;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 189;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 190;

UPDATE lecture SET enrl_cnt = 3 WHERE lec_no = 192;

UPDATE lecture SET enrl_cnt = 6 WHERE lec_no = 199;

UPDATE lecture SET enrl_cnt = 3 WHERE lec_no = 201;

UPDATE lecture SET enrl_cnt = 6 WHERE lec_no = 202;

UPDATE lecture SET enrl_cnt = 3 WHERE lec_no = 203;

UPDATE lecture SET enrl_cnt = 4 WHERE lec_no = 204;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 211;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 213;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 214;

UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = 215;

UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = 217;

UPDATE lecture SET enrl_cnt = 4 WHERE lec_no = 220;

UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = 221;

UPDATE lecture SET enrl_cnt = 3 WHERE lec_no = 222;

UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = 225;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 226;

UPDATE lecture SET enrl_cnt = 13 WHERE lec_no = 229;

UPDATE grade SET mid_scr = 88, fin_scr = 82, hw_scr = 81, att_scr = 100, tot_scr = 86, ltr_grd = 'B0' WHERE std_no = '20260004' AND lec_no = 184;

UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = '9001', cnfm_dtm = '2027-02-15 10:00:00' WHERE lec_no IN (184, 186);

UPDATE semester SET cur_sem_yn = 'N' WHERE sem_no = 9;

UPDATE semester SET cur_sem_yn = 'Y' WHERE sem_no = 11;

INSERT INTO grade (std_no, lec_no, mid_scr, fin_scr, hw_scr, att_scr, tot_scr, ltr_grd) VALUES
  ('20230002', 192, 77, 87, 78, 92, 82, 'B-'),
  ('20260001', 192, 78, 79, 95, 96, 81, 'B-'),
  ('20260003', 192, NULL, NULL, NULL, NULL, NULL, NULL),
  ('20240003', 201, 84, 77, 83, 97, 82, 'B-'),
  ('20250001', 201, 76, 95, 95, 97, 89, 'B+'),
  ('20270002', 201, 74, 73, 81, 97, 78, 'C+');

UPDATE lecture SET grd_stat = 'ENTERING' WHERE lec_no = 192;

UPDATE lecture SET grd_stat = 'SUBMITTED' WHERE lec_no = 201;
