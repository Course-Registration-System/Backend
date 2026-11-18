-- s3 정정 기간까지(2027-02-11 ~ 03-04 11:00): 2·1학년 수강신청, 현재학기 2027-1로 전환(03-02). tools/seed/build_seed.py 로 생성한다.

-- 앞 단계까지 적용한 뒤 한 번 적용한다. 시연 중 앱 기능으로 이미 처리한 내용(입력한 점수, 계산한 최대학점, 만든 계정)은 덮어쓰지 않는다.

SET NAMES utf8mb4;



INSERT INTO enrollment (std_no, lec_no, enrl_stat, retake_yn, proc_type, enrl_dtm) VALUES
  ('20270001', 187, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:04:00'),
  ('20270001', 189, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:07:00'),
  ('20260001', 190, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:01:00'),
  ('20260003', 190, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:03:00'),
  ('20260001', 192, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:07:00'),
  ('20260003', 192, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:09:00'),
  ('20270002', 199, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:05:00'),
  ('20270002', 201, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:08:00'),
  ('20250002', 202, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:02:00'),
  ('20260002', 202, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:02:00'),
  ('20260004', 202, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:04:00'),
  ('20250002', 203, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:05:00'),
  ('20260002', 203, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:05:00'),
  ('20260004', 203, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:07:00'),
  ('20250002', 204, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:08:00'),
  ('20260002', 204, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:08:00'),
  ('20260004', 204, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:10:00'),
  ('20270001', 211, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:01:00'),
  ('20270002', 211, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:02:00'),
  ('20270001', 213, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:10:00'),
  ('20270002', 213, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:11:00'),
  ('20270001', 214, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:13:00'),
  ('20270002', 214, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:14:00'),
  ('20270002', 215, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:17:00'),
  ('20260002', 216, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:17:00'),
  ('20250002', 217, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:17:00'),
  ('20260003', 217, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:12:00'),
  ('20260004', 217, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:19:00'),
  ('20270001', 217, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:19:00'),
  ('20260001', 220, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:10:00'),
  ('20260002', 220, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:11:00'),
  ('20260003', 220, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:15:00'),
  ('20270001', 221, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:16:00'),
  ('20270002', 221, 'ENROLLED', 'N', 'STUDENT', '2027-02-12 10:20:00'),
  ('20250002', 222, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:11:00'),
  ('20260002', 222, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:14:00'),
  ('20260003', 222, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:18:00'),
  ('20260004', 222, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:16:00'),
  ('20250002', 224, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:14:00'),
  ('20260001', 225, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:13:00'),
  ('20260001', 226, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:16:00'),
  ('20260004', 226, 'ENROLLED', 'N', 'STUDENT', '2027-02-11 10:13:00');

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

UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = 216;

UPDATE lecture SET enrl_cnt = 4 WHERE lec_no = 217;

UPDATE lecture SET enrl_cnt = 4 WHERE lec_no = 220;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 221;

UPDATE lecture SET enrl_cnt = 4 WHERE lec_no = 222;

UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = 224;

UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = 225;

UPDATE lecture SET enrl_cnt = 2 WHERE lec_no = 226;

UPDATE semester SET cur_sem_yn = 'N' WHERE sem_no = 9;

UPDATE semester SET cur_sem_yn = 'Y' WHERE sem_no = 11;
