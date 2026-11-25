-- LEC-04 조교는 대학원생(학번 10자리, V6)만 될 수 있다. 조교 추가(TeachingAssistantService)도 같은 규칙을 검사한다.
-- 대학원생은 장바구니·수강신청을 할 수 없으므로(V6) "그 강의의 수강생은 조교가 될 수 없다"는 규칙은 이 제약으로 함께 지켜진다.

-- 이미 쓰던 DB 에는 옛 시드의 학부생 조교 배정(20240005)이 남아 있어 아래 CHECK 를 추가할 수 없다. 시드(R__)는 이 파일 다음에 돌므로
-- 여기서 먼저 학부생 배정을 지운다. 새 DB 에서는 아무것도 지워지지 않는다.
DELETE FROM teaching_assistant_assignment WHERE CHAR_LENGTH(std_no) <> 10;

ALTER TABLE teaching_assistant_assignment
  ADD CONSTRAINT ck_teaching_assistant_assignment_graduate CHECK (CHAR_LENGTH(std_no) = 10);
