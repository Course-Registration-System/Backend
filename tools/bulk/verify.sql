-- 대량 데이터의 FK 고아 행 검사. 적재할 때 FK 검사를 꺼서 DB 가 확인하지 않은 부분이다. 모든 줄이 0 이어야 한다.
-- 업무 규칙 검사는 시드와 같은 src/test/resources/seed/seed-checks.sql 을 쓴다(load_bulk.py 가 이어서 실행).

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

SELECT 'FK semester_maximum_credit → student·semester', COUNT(*)
  FROM semester_maximum_credit m
  LEFT JOIN student s ON s.std_no = m.std_no
  LEFT JOIN semester se ON se.sem_no = m.sem_no
 WHERE s.std_no IS NULL OR se.sem_no IS NULL;
