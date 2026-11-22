-- 대량 데이터 검사. 적재할 때 FK 검사를 껐으므로 고아 행부터 확인한다. 모든 줄의 violations 가 0 이어야 한다.
-- 검사마다 따로 실행한다(UNION ALL 로 묶으면 옵티마이저가 느린 계획을 골라 S 규모에서 10분을 넘겼다).
-- load_bulk.py 가 적재 후 실행한다. 직접 돌릴 때: mysql crs_bulk < tools/bulk/verify.sql

SELECT 'FK student → user_account' AS chk, COUNT(*) AS violations
  FROM student s LEFT JOIN user_account u ON u.usr_no = s.std_no WHERE u.usr_no IS NULL;

SELECT 'FK professor → user_account', COUNT(*)
  FROM professor p LEFT JOIN user_account u ON u.usr_no = p.prof_no WHERE u.usr_no IS NULL;

SELECT 'FK lecture → course·semester·professor', COUNT(*)
  FROM lecture l
  LEFT JOIN course c ON c.crs_cd = l.crs_cd
  LEFT JOIN semester s ON s.sem_no = l.sem_no
  LEFT JOIN professor p ON p.prof_no = l.prof_no
 WHERE c.crs_cd IS NULL OR s.sem_no IS NULL OR p.prof_no IS NULL;

SELECT 'FK lecture_time_slot → lecture·classroom', COUNT(*)
  FROM lecture_time_slot t
  LEFT JOIN lecture l ON l.lec_no = t.lec_no
  LEFT JOIN classroom r ON r.room_cd = t.room_cd
 WHERE l.lec_no IS NULL OR (t.room_cd IS NOT NULL AND r.room_cd IS NULL);

SELECT 'FK enrollment → student·lecture', COUNT(*)
  FROM enrollment e
  LEFT JOIN student s ON s.std_no = e.std_no
  LEFT JOIN lecture l ON l.lec_no = e.lec_no
 WHERE s.std_no IS NULL OR l.lec_no IS NULL;

SELECT 'FK grade → enrollment', COUNT(*)
  FROM grade g LEFT JOIN enrollment e ON e.std_no = g.std_no AND e.lec_no = g.lec_no WHERE e.std_no IS NULL;

-- 신청인원 = 신청 건수, 정원 이내
SELECT 'enrl_cnt = 신청 건수', COUNT(*)
  FROM lecture l
  LEFT JOIN (SELECT lec_no, COUNT(*) AS n FROM enrollment WHERE enrl_stat = 'ENROLLED' GROUP BY lec_no) e
         ON e.lec_no = l.lec_no
 WHERE l.enrl_cnt <> COALESCE(e.n, 0);

SELECT '신청인원 ≤ 정원', COUNT(*) FROM lecture WHERE enrl_cnt > cap;

-- 성적은 신청 중인 건에만, 확정된 강의의 신청에는 모두 있다
SELECT '취소한 신청에 성적 없음', COUNT(*)
  FROM grade g JOIN enrollment e ON e.std_no = g.std_no AND e.lec_no = g.lec_no WHERE e.enrl_stat <> 'ENROLLED';

SELECT '확정 강의의 신청에는 성적', COUNT(*)
  FROM enrollment e
  JOIN lecture l ON l.lec_no = e.lec_no
  LEFT JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
 WHERE e.enrl_stat = 'ENROLLED' AND l.grd_stat = 'CONFIRMED' AND g.ltr_grd IS NULL;

-- 같은 학기에 같은 과목 두 분반 금지
SELECT '같은 학기 같은 과목 중복', COUNT(*) FROM (
    SELECT e.std_no, l.sem_no, l.crs_cd
      FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
     WHERE e.enrl_stat = 'ENROLLED'
     GROUP BY e.std_no, l.sem_no, l.crs_cd HAVING COUNT(*) > 1) x;

-- 한 학생의 같은 학기 강의끼리 시간이 겹치지 않는다
SELECT '학생 시간 중복', COUNT(*) FROM (
    SELECT 1
      FROM enrollment e1
      JOIN lecture l1 ON l1.lec_no = e1.lec_no
      JOIN lecture_time_slot t1 ON t1.lec_no = l1.lec_no
      JOIN enrollment e2 ON e2.std_no = e1.std_no AND e2.lec_no > e1.lec_no AND e2.enrl_stat = 'ENROLLED'
      JOIN lecture l2 ON l2.lec_no = e2.lec_no AND l2.sem_no = l1.sem_no
      JOIN lecture_time_slot t2 ON t2.lec_no = l2.lec_no AND t2.dow = t1.dow
                               AND t1.start_tm < t2.end_tm AND t2.start_tm < t1.end_tm
     WHERE e1.enrl_stat = 'ENROLLED'
     LIMIT 1000) x;

-- 같은 강의실·같은 시간에 대면 강의 하나
SELECT '강의실 시간 중복', COUNT(*) FROM (
    SELECT 1
      FROM lecture_time_slot t1
      JOIN lecture l1 ON l1.lec_no = t1.lec_no
      JOIN lecture_time_slot t2 ON t2.room_cd = t1.room_cd AND t2.dow = t1.dow AND t2.lec_no > t1.lec_no
                               AND t1.start_tm < t2.end_tm AND t2.start_tm < t1.end_tm
      JOIN lecture l2 ON l2.lec_no = t2.lec_no AND l2.sem_no = l1.sem_no
     WHERE t1.room_cd IS NOT NULL
     LIMIT 1000) x;

-- 최대학점(채플 제외): 정규 21, 계절 6
SELECT '최대학점 초과', COUNT(*) FROM (
    SELECT e.std_no, l.sem_no, s.sem_type, SUM(c.crdt) AS crd
      FROM enrollment e
      JOIN lecture l ON l.lec_no = e.lec_no
      JOIN semester s ON s.sem_no = l.sem_no
      JOIN course c ON c.crs_cd = l.crs_cd
     WHERE e.enrl_stat = 'ENROLLED' AND c.cmpl_type <> 'CHAPEL'
     GROUP BY e.std_no, l.sem_no, s.sem_type
    HAVING crd > CASE WHEN s.sem_type IN ('FIRST', 'SECOND') THEN 21 ELSE 6 END) x;

-- 재수강 성적은 A- 까지
SELECT '재수강 성적 A- 상한', COUNT(*)
  FROM enrollment e JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
 WHERE e.retake_yn = 'Y' AND g.ltr_grd IN ('A+', 'A0');

-- 재수강 표시는 이전에 들은 과목에만, 채플은 재수강이 아니다
SELECT '재수강 표시 = 이전 이수', COUNT(*) FROM (
    SELECT e.std_no, e.lec_no, e.retake_yn,
           EXISTS (SELECT 1 FROM enrollment p JOIN lecture pl ON pl.lec_no = p.lec_no
                     JOIN semester ps ON ps.sem_no = pl.sem_no
                    WHERE p.std_no = e.std_no AND p.enrl_stat = 'ENROLLED' AND pl.crs_cd = l.crs_cd
                      AND (ps.yr * 10 + FIELD(ps.sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER'))
                        < (s.yr * 10 + FIELD(s.sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER'))) AS had
      FROM enrollment e
      JOIN lecture l ON l.lec_no = e.lec_no
      JOIN semester s ON s.sem_no = l.sem_no
      JOIN course c ON c.crs_cd = l.crs_cd
     WHERE e.enrl_stat = 'ENROLLED' AND c.cmpl_type <> 'CHAPEL') x
 WHERE (retake_yn = 'Y') <> had;

-- P/F 과목은 P·F 만, 등급제 과목은 P 없음
SELECT '평가방식과 등급', COUNT(*)
  FROM grade g
  JOIN lecture l ON l.lec_no = g.lec_no
  JOIN course c ON c.crs_cd = l.crs_cd
 WHERE (c.eval_type = 'PASS_FAIL' AND g.ltr_grd NOT IN ('P', 'F')) OR (c.eval_type = 'GRADE' AND g.ltr_grd = 'P');

-- 상대평가: 10명 이상이고 제외가 아닌 등급제 강의는 A 40%, A+B 80% 이내
SELECT '상대평가 비율', COUNT(*) FROM (
    SELECT l.lec_no, COUNT(*) AS n,
           SUM(g.ltr_grd LIKE 'A%') AS a, SUM(g.ltr_grd LIKE 'A%' OR g.ltr_grd LIKE 'B%') AS ab
      FROM lecture l
      JOIN course c ON c.crs_cd = l.crs_cd
      JOIN grade g ON g.lec_no = l.lec_no
     WHERE c.eval_type = 'GRADE' AND l.rel_grd_exmp_yn = 'N'
     GROUP BY l.lec_no
    HAVING n >= 10 AND (a > FLOOR(n * 0.4) OR ab > FLOOR(n * 0.8))) x;

-- 학년은 1~4, 현재 학기는 하나
SELECT '현재 학기 하나', ABS(COUNT(*) - 1) FROM semester WHERE cur_sem_yn = 'Y';
