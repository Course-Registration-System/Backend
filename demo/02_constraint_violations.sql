-- 계정: app
-- 예외 상황: 앱 계정으로도 DB 제약을 어기는 데이터는 들어가지 않는다. 모두 거부돼야 한다.
-- "기대: 거부 X" 는 오류 메시지에 X(제약 이름)가 들어 있어야 한다는 뜻이다.

-- 기대: 거부 PRIMARY
-- 같은 학생이 같은 강의를 두 번 신청
INSERT INTO enrollment (std_no, lec_no, enrl_dtm) VALUES ('20240001', 196, '2027-02-10 18:00:00');

-- 기대: 거부 uk_semester_current
-- 현재 학기가 둘
UPDATE semester SET cur_sem_yn = 'Y' WHERE sem_no = 11;

-- 기대: 거부 ck_grade_mid_scr
-- 점수 101
UPDATE grade SET mid_scr = 101 WHERE std_no = '20240001' AND lec_no = 157;

-- 기대: 거부 ck_grade_pf_no_score
-- P/F 과목(사회봉사)에 점수 입력
UPDATE grade SET mid_scr = 90 WHERE std_no = '20250003' AND lec_no = 173;

-- 기대: 거부 ck_course_crdt
-- 학점 5 (0.5~4만 허용)
UPDATE course SET crdt = 5 WHERE crs_cd = '1010014';

-- 기대: 거부 ck_lecture_weight_sum
-- 평가 반영비율 합이 100이 아님
UPDATE lecture SET att_wt = att_wt - 10 WHERE lec_no = 191;

-- 기대: 거부 ck_lecture_time_slot_online_no_room
-- 온라인 수업에 강의실 지정
UPDATE lecture_time_slot SET room_cd = '21301' WHERE lec_no = 222;

-- 기대: 거부 fk_lecture_time_slot_classroom
-- 등록되지 않은 강의실
UPDATE lecture_time_slot SET room_cd = '99999' WHERE lec_no = 225;

-- 기대: 거부 ck_semester_maximum_credit_max_crdt_value
-- 최대학점 20 (정규 18·21, 계절 6·3만 허용)
UPDATE semester_maximum_credit SET max_crdt = 20 WHERE std_no = '20240001' AND sem_no = 11;

-- 기대: 거부 ck_academic_schedule_period_order
-- 종료가 시작보다 빠른 일정
UPDATE academic_schedule SET end_dtm = '2027-02-01 00:00:00' WHERE schd_no = 68;
