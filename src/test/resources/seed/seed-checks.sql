-- 시드 정합성 검사. DB 제약으로 막지 못하는 업무 규칙을 SQL로 센다.
-- 각 검사는 'CHECK 이름 | 기대값' 주석 줄 다음의 SELECT 하나이고, 위반 건수(숫자 하나)를 돌려준다.
-- run_seed_check.py가 순서대로 실행해 기대값과 비교한다. 대량 데이터·품질 검사(E-3)에서도 그대로 쓴다.
-- @now(스냅숏 시각)는 실행기가 먼저 정한다.

-- CHECK 신청인원 카운터 = 신청 건수 | 0
SELECT COUNT(*) FROM lecture l
WHERE l.enrl_cnt <> (SELECT COUNT(*) FROM enrollment e WHERE e.lec_no = l.lec_no AND e.enrl_stat = 'ENROLLED');

-- CHECK 정원 초과 | 0
SELECT COUNT(*) FROM lecture WHERE enrl_cnt > cap;

-- CHECK 취소한 신청에 성적 있음 | 0
SELECT COUNT(*) FROM grade g JOIN enrollment e USING (std_no, lec_no) WHERE e.enrl_stat = 'CANCELLED';

-- CHECK 확정 강의인데 성적이 빈 수강생 | 0
SELECT COUNT(*) FROM enrollment e JOIN lecture l USING (lec_no) LEFT JOIN grade g USING (std_no, lec_no)
WHERE l.grd_stat = 'CONFIRMED' AND e.enrl_stat = 'ENROLLED' AND g.ltr_grd IS NULL;

-- CHECK 한 학생의 수업 시간 겹침 | 0
WITH s AS (
  SELECT e.std_no, l.sem_no, t.lec_no, t.dow, t.start_tm, t.end_tm
  FROM enrollment e JOIN lecture l USING (lec_no) JOIN lecture_time_slot t USING (lec_no)
  WHERE e.enrl_stat = 'ENROLLED')
SELECT COUNT(*) FROM s a JOIN s b
  ON a.std_no = b.std_no AND a.sem_no = b.sem_no AND a.lec_no < b.lec_no
 AND a.dow = b.dow AND a.start_tm < b.end_tm AND b.start_tm < a.end_tm;

-- CHECK 강의실 이중 배정 | 0
WITH s AS (
  SELECT l.sem_no, t.lec_no, t.room_cd, t.dow, t.start_tm, t.end_tm
  FROM lecture_time_slot t JOIN lecture l USING (lec_no)
  WHERE t.room_cd IS NOT NULL AND l.lec_stat = 'OPEN')
SELECT COUNT(*) FROM s a JOIN s b
  ON a.sem_no = b.sem_no AND a.room_cd = b.room_cd AND a.lec_no < b.lec_no
 AND a.dow = b.dow AND a.start_tm < b.end_tm AND b.start_tm < a.end_tm;

-- CHECK 교수 시간 겹침 | 0
WITH s AS (
  SELECT l.sem_no, l.prof_no, t.lec_no, t.dow, t.start_tm, t.end_tm
  FROM lecture_time_slot t JOIN lecture l USING (lec_no) WHERE l.lec_stat = 'OPEN')
SELECT COUNT(*) FROM s a JOIN s b
  ON a.sem_no = b.sem_no AND a.prof_no = b.prof_no AND a.lec_no < b.lec_no
 AND a.dow = b.dow AND a.start_tm < b.end_tm AND b.start_tm < a.end_tm;

-- CHECK 최대학점 초과(채플 제외) | 0
SELECT COUNT(*) FROM (
  SELECT e.std_no, l.sem_no, SUM(c.crdt) AS crdt
  FROM enrollment e JOIN lecture l USING (lec_no) JOIN course c USING (crs_cd)
  WHERE e.enrl_stat = 'ENROLLED' AND c.cmpl_type <> 'CHAPEL'
  GROUP BY e.std_no, l.sem_no) x
LEFT JOIN semester_maximum_credit m ON m.std_no = x.std_no AND m.sem_no = x.sem_no
WHERE x.crdt > COALESCE(m.max_crdt, 18);

-- CHECK 같은 학기 같은 과목 중복 신청 | 0
SELECT COUNT(*) FROM (
  SELECT e.std_no, l.sem_no, l.crs_cd FROM enrollment e JOIN lecture l USING (lec_no)
  WHERE e.enrl_stat = 'ENROLLED' GROUP BY e.std_no, l.sem_no, l.crs_cd HAVING COUNT(*) > 1) x;

-- CHECK 재수강 성적이 A- 초과 | 0
SELECT COUNT(*) FROM grade g JOIN enrollment e USING (std_no, lec_no)
WHERE e.retake_yn = 'Y' AND g.ltr_grd IN ('A+', 'A0');

-- CHECK 재수강 조건(이전 확정 성적 C+ 이하, P/F는 F) 위반 또는 재수강 표시 누락 | 0
WITH ord AS (SELECT sem_no, yr * 10 + FIELD(sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER') AS o FROM semester),
att AS (
  SELECT e.std_no, l.crs_cd, ord.o, g.ltr_grd
  FROM grade g JOIN enrollment e USING (std_no, lec_no) JOIN lecture l USING (lec_no) JOIN ord USING (sem_no)
  WHERE l.grd_stat = 'CONFIRMED' AND e.enrl_stat = 'ENROLLED'),
cur AS (
  SELECT e.std_no, l.crs_cd, ord.o, e.retake_yn,
         (SELECT a.ltr_grd FROM att a WHERE a.std_no = e.std_no AND a.crs_cd = l.crs_cd AND a.o < ord.o
           ORDER BY a.o DESC LIMIT 1) AS prev
  FROM enrollment e JOIN lecture l USING (lec_no) JOIN ord USING (sem_no) WHERE e.enrl_stat = 'ENROLLED')
SELECT COUNT(*) FROM cur
WHERE (retake_yn = 'Y' AND (prev IS NULL OR FIELD(prev, 'A+', 'A0', 'A-', 'B+', 'B0', 'B-', 'C+') BETWEEN 1 AND 6 OR prev = 'P'))
   OR (retake_yn = 'N' AND prev IS NOT NULL);

-- CHECK 선수과목 미이수 신청 | 0
WITH ord AS (SELECT sem_no, yr * 10 + FIELD(sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER') AS o FROM semester),
att AS (
  SELECT e.std_no, l.crs_cd, ord.o, g.ltr_grd
  FROM grade g JOIN enrollment e USING (std_no, lec_no) JOIN lecture l USING (lec_no) JOIN ord USING (sem_no)
  WHERE l.grd_stat = 'CONFIRMED' AND e.enrl_stat = 'ENROLLED'),
need AS (
  SELECT e.std_no, ord.o, p.prereq_crs_cd, p.min_req_grd,
         (SELECT a.ltr_grd FROM att a WHERE a.std_no = e.std_no AND a.crs_cd = p.prereq_crs_cd AND a.o < ord.o
           ORDER BY a.o DESC LIMIT 1) AS got
  FROM enrollment e JOIN lecture l USING (lec_no) JOIN ord USING (sem_no)
  JOIN prerequisite_course p ON p.crs_cd = l.crs_cd
  WHERE e.enrl_stat = 'ENROLLED')
SELECT COUNT(*) FROM need
WHERE got IS NULL OR got = 'F'
   OR FIELD(got, 'A+', 'A0', 'A-', 'B+', 'B0', 'B-', 'C+', 'C0', 'C-', 'D+', 'D0', 'D-')
    > FIELD(min_req_grd, 'A+', 'A0', 'A-', 'B+', 'B0', 'B-', 'C+', 'C0', 'C-', 'D+', 'D0', 'D-');

-- CHECK 상대평가 비율 초과(확정 강의) | 0
SELECT COUNT(*) FROM (
  SELECT l.lec_no, c.cmpl_type, COUNT(*) AS n,
         SUM(g.ltr_grd IN ('A+', 'A0', 'A-')) AS a,
         SUM(g.ltr_grd IN ('A+', 'A0', 'A-', 'B+', 'B0', 'B-')) AS ab
  FROM lecture l JOIN course c USING (crs_cd)
  JOIN enrollment e ON e.lec_no = l.lec_no AND e.enrl_stat = 'ENROLLED'
  JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
  WHERE l.grd_stat = 'CONFIRMED' AND c.eval_type = 'GRADE' AND l.rel_grd_exmp_yn = 'N'
    AND NOT (l.eng_lec_yn = 'Y' AND c.cmpl_type IN ('MAJOR_BASIC', 'MAJOR_REQ', 'MAJOR_ELEC'))
  GROUP BY l.lec_no, c.cmpl_type HAVING COUNT(*) >= 10) x
WHERE (cmpl_type = 'TEACHING' AND a > FLOOR(n * 0.5))
   OR (cmpl_type <> 'TEACHING' AND (a > FLOOR(n * 0.4) OR ab > FLOOR(n * 0.8)));

-- CHECK 역할과 하위 테이블 불일치 | 0
SELECT COUNT(*) FROM user_account u
LEFT JOIN student s ON s.std_no = u.usr_no
LEFT JOIN professor p ON p.prof_no = u.usr_no
LEFT JOIN administrator a ON a.emp_no = u.usr_no
WHERE (u.role = 'STUDENT') <> (s.std_no IS NOT NULL)
   OR (u.role = 'PROFESSOR') <> (p.prof_no IS NOT NULL)
   OR (u.role = 'ADMIN') <> (a.emp_no IS NOT NULL);

-- CHECK 현재 학기는 하나 | 0
SELECT ABS(COUNT(*) - 1) FROM semester WHERE cur_sem_yn = 'Y';

-- CHECK 신청 대상 학기가 2027-1 | 0
WITH ord AS (SELECT sem_no, yr, sem_type, yr * 10 + FIELD(sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER') AS o FROM semester)
SELECT COUNT(*) FROM (
  SELECT ord.yr, ord.sem_type FROM ord JOIN academic_schedule a USING (sem_no)
  WHERE a.schd_type IN ('CART', 'ENROLL', 'ADJUST') AND a.end_dtm > @now
  ORDER BY ord.o LIMIT 1) t
WHERE NOT (t.yr = 2027 AND t.sem_type = 'FIRST');

-- CHECK 신청 대상 학기 신청이 장바구니에 없음 | 0
SELECT COUNT(*) FROM enrollment e JOIN lecture l USING (lec_no) JOIN semester s USING (sem_no)
LEFT JOIN course_cart c ON c.std_no = e.std_no AND c.lec_no = e.lec_no
WHERE s.yr = 2027 AND s.sem_type = 'FIRST' AND c.std_no IS NULL;

-- CHECK 학년 = min(4, 다닌 정규학기 수 / 2 + 1) | 0
SELECT COUNT(*) FROM student st
LEFT JOIN (
  SELECT e.std_no, COUNT(DISTINCT l.sem_no) AS n
  FROM enrollment e JOIN lecture l USING (lec_no) JOIN semester s USING (sem_no)
  WHERE e.enrl_stat = 'ENROLLED' AND l.grd_stat = 'CONFIRMED' AND s.sem_type IN ('FIRST', 'SECOND')
  GROUP BY e.std_no) x ON x.std_no = st.std_no
WHERE st.yr_lvl <> LEAST(4, FLOOR(COALESCE(x.n, 0) / 2) + 1);

-- CHECK 졸업 판정(정규 8학기 이상, 이수 130학점 이상) 불일치 | 0
WITH ord AS (SELECT sem_no, yr * 10 + FIELD(sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER') AS o FROM semester),
att AS (
  SELECT e.std_no, l.crs_cd, c.crdt, g.ltr_grd,
         ROW_NUMBER() OVER (PARTITION BY e.std_no, l.crs_cd ORDER BY ord.o DESC) AS rn
  FROM grade g JOIN enrollment e USING (std_no, lec_no) JOIN lecture l USING (lec_no)
  JOIN course c USING (crs_cd) JOIN ord USING (sem_no)
  WHERE l.grd_stat = 'CONFIRMED' AND e.enrl_stat = 'ENROLLED'),
crd AS (SELECT std_no, SUM(crdt) AS crdt FROM att WHERE rn = 1 AND ltr_grd <> 'F' GROUP BY std_no),
sem AS (
  SELECT e.std_no, COUNT(DISTINCT l.sem_no) AS n
  FROM enrollment e JOIN lecture l USING (lec_no) JOIN semester s USING (sem_no)
  WHERE e.enrl_stat = 'ENROLLED' AND l.grd_stat = 'CONFIRMED' AND s.sem_type IN ('FIRST', 'SECOND')
  GROUP BY e.std_no)
SELECT COUNT(*) FROM student st
LEFT JOIN crd ON crd.std_no = st.std_no LEFT JOIN sem ON sem.std_no = st.std_no
WHERE st.std_stat <> 'ON_LEAVE'
  AND (st.std_stat = 'GRADUATED') <> (COALESCE(sem.n, 0) >= 8 AND COALESCE(crd.crdt, 0) >= 130);

-- CHECK 졸업생의 신청 대상 학기 장바구니·신청·최대학점·조교 | 0
SELECT
  (SELECT COUNT(*) FROM course_cart c JOIN lecture l USING (lec_no) JOIN student s ON s.std_no = c.std_no
    WHERE s.std_stat = 'GRADUATED' AND l.sem_no = (SELECT sem_no FROM semester WHERE yr = 2027 AND sem_type = 'FIRST'))
+ (SELECT COUNT(*) FROM enrollment e JOIN lecture l USING (lec_no) JOIN student s ON s.std_no = e.std_no
    WHERE s.std_stat = 'GRADUATED' AND l.sem_no = (SELECT sem_no FROM semester WHERE yr = 2027 AND sem_type = 'FIRST'))
+ (SELECT COUNT(*) FROM semester_maximum_credit m JOIN student s USING (std_no)
    WHERE s.std_stat = 'GRADUATED' AND m.sem_no = (SELECT sem_no FROM semester WHERE yr = 2027 AND sem_type = 'FIRST'))
+ (SELECT COUNT(*) FROM teaching_assistant_assignment t JOIN lecture l USING (lec_no) JOIN student s ON s.std_no = t.std_no
    WHERE s.std_stat = 'GRADUATED' AND l.sem_no = (SELECT sem_no FROM semester WHERE yr = 2027 AND sem_type = 'FIRST'));

-- CHECK 2027-1 최대학점 재계산과 다름(직전 다닌 정규학기 평점 4.0 이상이면 21) | 0
WITH ord AS (SELECT sem_no, sem_type, yr * 10 + FIELD(sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER') AS o FROM semester),
tgt AS (SELECT sem_no, o FROM ord WHERE o = 20271),
gpa AS (
  SELECT e.std_no, l.sem_no, ord.o,
         SUM(c.crdt * CASE g.ltr_grd WHEN 'A+' THEN 4.5 WHEN 'A0' THEN 4.3 WHEN 'A-' THEN 4.0 WHEN 'B+' THEN 3.5
           WHEN 'B0' THEN 3.3 WHEN 'B-' THEN 3.0 WHEN 'C+' THEN 2.5 WHEN 'C0' THEN 2.3 WHEN 'C-' THEN 2.0
           WHEN 'D+' THEN 1.5 WHEN 'D0' THEN 1.3 WHEN 'D-' THEN 1.0 ELSE 0 END)
           / NULLIF(SUM(CASE WHEN c.eval_type = 'GRADE' THEN c.crdt END), 0) AS gpa
  FROM grade g JOIN enrollment e USING (std_no, lec_no) JOIN lecture l USING (lec_no)
  JOIN course c USING (crs_cd) JOIN ord USING (sem_no)
  WHERE l.grd_stat = 'CONFIRMED' AND e.enrl_stat = 'ENROLLED' AND ord.sem_type IN ('FIRST', 'SECOND')
    AND (c.eval_type = 'GRADE' OR g.ltr_grd = 'P')
  GROUP BY e.std_no, l.sem_no, ord.o),
last AS (
  SELECT std_no, gpa, ROW_NUMBER() OVER (PARTITION BY std_no ORDER BY o DESC) AS rn
  FROM gpa WHERE o < (SELECT o FROM tgt)),
expect AS (
  SELECT st.std_no, CASE WHEN l.gpa >= 4.0 THEN 21 ELSE 18 END AS mx
  FROM student st LEFT JOIN last l ON l.std_no = st.std_no AND l.rn = 1
  WHERE st.std_stat = 'ATTENDING')
SELECT
  (SELECT COUNT(*) FROM expect x LEFT JOIN semester_maximum_credit m
     ON m.std_no = x.std_no AND m.sem_no = (SELECT sem_no FROM tgt)
   WHERE m.max_crdt IS NULL OR m.max_crdt <> x.mx)
+ (SELECT COUNT(*) FROM semester_maximum_credit m JOIN student st USING (std_no)
   WHERE m.sem_no = (SELECT sem_no FROM tgt) AND st.std_stat <> 'ATTENDING');

-- CHECK 2026-2 평점: 20240001은 정확히 4.0, 20240002는 4.0 미만 | 0
SELECT SUM(CASE WHEN (std_no = '20240001' AND gpa <> 4.0) OR (std_no = '20240002' AND gpa >= 4.0) THEN 1 ELSE 0 END)
FROM (
  SELECT e.std_no, SUM(c.crdt * CASE g.ltr_grd WHEN 'A+' THEN 4.5 WHEN 'A0' THEN 4.3 WHEN 'A-' THEN 4.0
           WHEN 'B+' THEN 3.5 WHEN 'B0' THEN 3.3 ELSE 0 END) / SUM(c.crdt) AS gpa
  FROM grade g JOIN enrollment e USING (std_no, lec_no) JOIN lecture l USING (lec_no)
  JOIN course c USING (crs_cd) JOIN semester s USING (sem_no)
  WHERE s.yr = 2026 AND s.sem_type = 'SECOND' AND c.eval_type = 'GRADE' AND e.std_no IN ('20240001', '20240002')
    AND e.enrl_stat = 'ENROLLED'
  GROUP BY e.std_no) x;

-- CHECK 신청이 시작된 신청 대상 학기에 강의실 미배정 대면 수업(의도한 사례 1건) | 1
SELECT COUNT(DISTINCT t.lec_no) FROM lecture_time_slot t JOIN lecture l USING (lec_no) JOIN semester s USING (sem_no)
WHERE s.yr = 2027 AND s.sem_type = 'FIRST' AND l.lec_stat = 'OPEN' AND t.cls_type = 'OFFLINE' AND t.room_cd IS NULL
  AND EXISTS (SELECT 1 FROM academic_schedule a WHERE a.sem_no = l.sem_no AND a.schd_type = 'ENROLL' AND a.start_dtm <= @now);
