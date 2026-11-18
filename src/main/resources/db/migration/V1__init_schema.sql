-- 수강신청 및 성적관리 시스템 물리 스키마 (MySQL 8.4, InnoDB, utf8mb4)
-- build_physical.py로 생성한다. 직접 고치지 말고 생성기를 고친다.

SET NAMES utf8mb4;

CREATE TABLE user_account (
  usr_no VARCHAR(10) NOT NULL COMMENT '사용자번호',
  pwd VARCHAR(255) NOT NULL COMMENT '비밀번호',
  usr_nm VARCHAR(50) NOT NULL COMMENT '이름',
  email VARCHAR(100) NULL COMMENT '이메일',
  role VARCHAR(10) NOT NULL COMMENT '역할: STUDENT(학생), PROFESSOR(교수), ADMIN(관리자)',
  acct_stat VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '계정상태: ACTIVE(활성), INACTIVE(비활성)',
  CONSTRAINT pk_user_account PRIMARY KEY (usr_no),
  CONSTRAINT ck_user_account_role CHECK (role IN ('STUDENT', 'PROFESSOR', 'ADMIN')),
  CONSTRAINT ck_user_account_acct_stat CHECK (acct_stat IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자';

CREATE TABLE department (
  dept_no INT NOT NULL AUTO_INCREMENT COMMENT '학과번호',
  dept_nm VARCHAR(50) NOT NULL COMMENT '학과명',
  CONSTRAINT pk_department PRIMARY KEY (dept_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학과';

CREATE TABLE student (
  std_no VARCHAR(10) NOT NULL COMMENT '학번',
  dept_no INT NOT NULL COMMENT '학과번호',
  yr_lvl TINYINT NOT NULL COMMENT '학년',
  std_stat VARCHAR(10) NOT NULL DEFAULT 'ATTENDING' COMMENT '학적상태: ATTENDING(재학), ON_LEAVE(휴학), GRADUATED(졸업)',
  CONSTRAINT pk_student PRIMARY KEY (std_no),
  CONSTRAINT ck_student_yr_lvl CHECK (yr_lvl BETWEEN 1 AND 4),
  CONSTRAINT ck_student_std_stat CHECK (std_stat IN ('ATTENDING', 'ON_LEAVE', 'GRADUATED')),
  CONSTRAINT fk_student_user_account FOREIGN KEY (std_no) REFERENCES user_account (usr_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_student_department FOREIGN KEY (dept_no) REFERENCES department (dept_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학생';

CREATE TABLE professor (
  prof_no VARCHAR(10) NOT NULL COMMENT '교번',
  dept_no INT NOT NULL COMMENT '학과번호',
  office VARCHAR(50) NULL COMMENT '연구실',
  CONSTRAINT pk_professor PRIMARY KEY (prof_no),
  CONSTRAINT fk_professor_user_account FOREIGN KEY (prof_no) REFERENCES user_account (usr_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_professor_department FOREIGN KEY (dept_no) REFERENCES department (dept_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='교수';

CREATE TABLE administrator (
  emp_no VARCHAR(10) NOT NULL COMMENT '직번',
  CONSTRAINT pk_administrator PRIMARY KEY (emp_no),
  CONSTRAINT fk_administrator_user_account FOREIGN KEY (emp_no) REFERENCES user_account (usr_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자';

CREATE TABLE semester (
  sem_no INT NOT NULL AUTO_INCREMENT COMMENT '학기번호',
  yr SMALLINT NOT NULL COMMENT '연도',
  sem_type VARCHAR(10) NOT NULL COMMENT '학기구분: FIRST(1학기), SECOND(2학기), SUMMER(여름학기), WINTER(겨울학기)',
  cur_sem_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '현재학기여부: Y(예), N(아니오)',
  CONSTRAINT pk_semester PRIMARY KEY (sem_no),
  CONSTRAINT uk_semester_1 UNIQUE (yr, sem_type),
  UNIQUE KEY uk_semester_current (((CASE WHEN cur_sem_yn = 'Y' THEN 1 END))),
  CONSTRAINT ck_semester_yr CHECK (yr BETWEEN 2000 AND 2100),
  CONSTRAINT ck_semester_sem_type CHECK (sem_type IN ('FIRST', 'SECOND', 'SUMMER', 'WINTER')),
  CONSTRAINT ck_semester_cur_sem_yn CHECK (cur_sem_yn IN ('Y', 'N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학기';

CREATE TABLE academic_schedule (
  schd_no INT NOT NULL AUTO_INCREMENT COMMENT '일정번호',
  sem_no INT NOT NULL COMMENT '학기번호',
  schd_type VARCHAR(10) NOT NULL COMMENT '일정유형: CART(장바구니), ENROLL(수강신청), ADJUST(정정), GRADE(성적입력)',
  tgt_yr_lvl TINYINT NULL COMMENT '대상학년',
  start_dtm DATETIME NOT NULL COMMENT '시작일시',
  end_dtm DATETIME NOT NULL COMMENT '종료일시',
  CONSTRAINT pk_academic_schedule PRIMARY KEY (schd_no),
  CONSTRAINT ck_academic_schedule_schd_type CHECK (schd_type IN ('CART', 'ENROLL', 'ADJUST', 'GRADE')),
  CONSTRAINT ck_academic_schedule_tgt_yr_lvl CHECK (tgt_yr_lvl BETWEEN 1 AND 4),
  CONSTRAINT ck_academic_schedule_period_order CHECK (start_dtm < end_dtm),
  INDEX ix_academic_schedule_sem_type (sem_no, schd_type),
  CONSTRAINT fk_academic_schedule_semester FOREIGN KEY (sem_no) REFERENCES semester (sem_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학사일정';

CREATE TABLE semester_maximum_credit (
  std_no VARCHAR(10) NOT NULL COMMENT '학번',
  sem_no INT NOT NULL COMMENT '학기번호',
  max_crdt TINYINT NOT NULL COMMENT '최대학점',
  CONSTRAINT pk_semester_maximum_credit PRIMARY KEY (std_no, sem_no),
  CONSTRAINT ck_semester_maximum_credit_max_crdt_value CHECK (max_crdt IN (3, 6, 18, 21)),
  CONSTRAINT fk_semester_maximum_credit_student FOREIGN KEY (std_no) REFERENCES student (std_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_semester_maximum_credit_semester FOREIGN KEY (sem_no) REFERENCES semester (sem_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='학기별최대학점';

CREATE TABLE course (
  crs_cd VARCHAR(10) NOT NULL COMMENT '과목코드',
  dept_no INT NOT NULL COMMENT '학과번호',
  crs_nm VARCHAR(100) NOT NULL COMMENT '과목명',
  crdt DECIMAL(2,1) NOT NULL COMMENT '학점',
  cmpl_type VARCHAR(12) NOT NULL COMMENT '이수구분: MAJOR_BASIC(전공기초), MAJOR_REQ(전공필수), MAJOR_ELEC(전공선택), GEN_REQ(교양필수), GEN_ELEC(교양선택), TEACHING(교직), FREE_ELEC(일반선택), CHAPEL(채플)',
  eval_type VARCHAR(10) NOT NULL DEFAULT 'GRADE' COMMENT '평가방식: GRADE(등급제), PASS_FAIL(P/F)',
  rcmd_yr_lvl TINYINT NULL COMMENT '권장학년',
  CONSTRAINT pk_course PRIMARY KEY (crs_cd),
  CONSTRAINT ck_course_crdt CHECK (crdt BETWEEN 0.5 AND 4),
  CONSTRAINT ck_course_cmpl_type CHECK (cmpl_type IN ('MAJOR_BASIC', 'MAJOR_REQ', 'MAJOR_ELEC', 'GEN_REQ', 'GEN_ELEC', 'TEACHING', 'FREE_ELEC', 'CHAPEL')),
  CONSTRAINT ck_course_eval_type CHECK (eval_type IN ('GRADE', 'PASS_FAIL')),
  CONSTRAINT ck_course_rcmd_yr_lvl CHECK (rcmd_yr_lvl BETWEEN 1 AND 4),
  CONSTRAINT ck_course_crdt_step CHECK (crdt * 2 = FLOOR(crdt * 2)),
  CONSTRAINT fk_course_department FOREIGN KEY (dept_no) REFERENCES department (dept_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='과목';

CREATE TABLE prerequisite_course (
  crs_cd VARCHAR(10) NOT NULL COMMENT '과목코드',
  prereq_crs_cd VARCHAR(10) NOT NULL COMMENT '선수과목코드',
  min_req_grd VARCHAR(2) NOT NULL DEFAULT 'D-' COMMENT '최소취득등급',
  CONSTRAINT pk_prerequisite_course PRIMARY KEY (crs_cd, prereq_crs_cd),
  CONSTRAINT ck_prerequisite_course_min_req_grd CHECK (min_req_grd IN ('A+', 'A0', 'A-', 'B+', 'B0', 'B-', 'C+', 'C0', 'C-', 'D+', 'D0', 'D-')),
  CONSTRAINT ck_prerequisite_course_not_self CHECK (crs_cd <> prereq_crs_cd),
  CONSTRAINT fk_prerequisite_course_course FOREIGN KEY (crs_cd) REFERENCES course (crs_cd) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_prerequisite_course_course_prereq FOREIGN KEY (prereq_crs_cd) REFERENCES course (crs_cd) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='선수과목';

CREATE TABLE classroom (
  room_cd CHAR(5) NOT NULL COMMENT '강의실코드',
  room_nm VARCHAR(50) NOT NULL COMMENT '강의실명',
  CONSTRAINT pk_classroom PRIMARY KEY (room_cd),
  CONSTRAINT uk_classroom_1 UNIQUE (room_nm),
  CONSTRAINT ck_classroom_code_format CHECK (room_cd REGEXP '^[0-9]{5}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의실';

CREATE TABLE lecture (
  lec_no INT NOT NULL AUTO_INCREMENT COMMENT '강의번호',
  crs_cd VARCHAR(10) NOT NULL COMMENT '과목코드',
  sem_no INT NOT NULL COMMENT '학기번호',
  prof_no VARCHAR(10) NOT NULL COMMENT '교번',
  sect_no TINYINT NOT NULL COMMENT '분반',
  cap SMALLINT NOT NULL COMMENT '정원',
  enrl_cnt SMALLINT NOT NULL DEFAULT 0 COMMENT '신청인원',
  lec_stat VARCHAR(10) NOT NULL DEFAULT 'OPEN' COMMENT '강의상태: OPEN(개설), CLOSED(폐강)',
  eng_lec_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '영어강의여부: Y(예), N(아니오)',
  rel_grd_exmp_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '상대평가제외여부: Y(예), N(아니오)',
  plan_file VARCHAR(255) NULL COMMENT '계획서파일',
  mid_wt TINYINT NULL COMMENT '중간반영비율',
  fin_wt TINYINT NULL COMMENT '기말반영비율',
  hw_wt TINYINT NULL COMMENT '과제반영비율',
  att_wt TINYINT NULL COMMENT '출석반영비율',
  grd_stat VARCHAR(12) NOT NULL DEFAULT 'NOT_ENTERED' COMMENT '성적상태: NOT_ENTERED(미입력), ENTERING(입력중), SUBMITTED(제출), CONFIRMED(확정)',
  cnfm_emp_no VARCHAR(10) NULL COMMENT '확정자직번',
  cnfm_dtm DATETIME NULL COMMENT '확정일시',
  CONSTRAINT pk_lecture PRIMARY KEY (lec_no),
  CONSTRAINT uk_lecture_1 UNIQUE (sem_no, crs_cd, sect_no),
  CONSTRAINT ck_lecture_sect_no CHECK (sect_no BETWEEN 1 AND 99),
  CONSTRAINT ck_lecture_cap CHECK (cap BETWEEN 1 AND 999),
  CONSTRAINT ck_lecture_enrl_cnt CHECK (enrl_cnt BETWEEN 0 AND 999),
  CONSTRAINT ck_lecture_lec_stat CHECK (lec_stat IN ('OPEN', 'CLOSED')),
  CONSTRAINT ck_lecture_eng_lec_yn CHECK (eng_lec_yn IN ('Y', 'N')),
  CONSTRAINT ck_lecture_rel_grd_exmp_yn CHECK (rel_grd_exmp_yn IN ('Y', 'N')),
  CONSTRAINT ck_lecture_mid_wt CHECK (mid_wt BETWEEN 0 AND 100),
  CONSTRAINT ck_lecture_fin_wt CHECK (fin_wt BETWEEN 0 AND 100),
  CONSTRAINT ck_lecture_hw_wt CHECK (hw_wt BETWEEN 0 AND 100),
  CONSTRAINT ck_lecture_att_wt CHECK (att_wt BETWEEN 0 AND 100),
  CONSTRAINT ck_lecture_grd_stat CHECK (grd_stat IN ('NOT_ENTERED', 'ENTERING', 'SUBMITTED', 'CONFIRMED')),
  CONSTRAINT ck_lecture_weight_all_or_none CHECK ((mid_wt IS NULL) = (fin_wt IS NULL) AND (mid_wt IS NULL) = (hw_wt IS NULL) AND (mid_wt IS NULL) = (att_wt IS NULL)),
  CONSTRAINT ck_lecture_weight_sum CHECK (mid_wt + fin_wt + hw_wt + att_wt = 100),
  CONSTRAINT ck_lecture_confirm_time CHECK ((grd_stat = 'CONFIRMED') = (cnfm_dtm IS NOT NULL)),
  INDEX ix_lecture_prof_sem (prof_no, sem_no),
  CONSTRAINT fk_lecture_course FOREIGN KEY (crs_cd) REFERENCES course (crs_cd) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_lecture_semester FOREIGN KEY (sem_no) REFERENCES semester (sem_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_lecture_professor FOREIGN KEY (prof_no) REFERENCES professor (prof_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_lecture_administrator_cnfm FOREIGN KEY (cnfm_emp_no) REFERENCES administrator (emp_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의';

CREATE TABLE lecture_time_slot (
  lec_no INT NOT NULL COMMENT '강의번호',
  dow VARCHAR(3) NOT NULL COMMENT '요일: MON(월), TUE(화), WED(수), THU(목), FRI(금), SAT(토), SUN(일)',
  start_tm TIME NOT NULL COMMENT '시작시각',
  end_tm TIME NOT NULL COMMENT '종료시각',
  cls_type VARCHAR(10) NOT NULL DEFAULT 'OFFLINE' COMMENT '수업유형: OFFLINE(대면), ONLINE(온라인)',
  room_cd CHAR(5) NULL COMMENT '강의실코드',
  CONSTRAINT pk_lecture_time_slot PRIMARY KEY (lec_no, dow, start_tm),
  CONSTRAINT ck_lecture_time_slot_dow CHECK (dow IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
  CONSTRAINT ck_lecture_time_slot_cls_type CHECK (cls_type IN ('OFFLINE', 'ONLINE')),
  CONSTRAINT ck_lecture_time_slot_online_no_room CHECK (cls_type <> 'ONLINE' OR room_cd IS NULL),
  CONSTRAINT ck_lecture_time_slot_time_order CHECK (start_tm < end_tm),
  INDEX ix_lecture_time_slot_room_dow (room_cd, dow),
  CONSTRAINT fk_lecture_time_slot_lecture FOREIGN KEY (lec_no) REFERENCES lecture (lec_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_lecture_time_slot_classroom FOREIGN KEY (room_cd) REFERENCES classroom (room_cd) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='강의시간';

CREATE TABLE course_cart (
  std_no VARCHAR(10) NOT NULL COMMENT '학번',
  lec_no INT NOT NULL COMMENT '강의번호',
  reg_dtm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  CONSTRAINT pk_course_cart PRIMARY KEY (std_no, lec_no),
  INDEX ix_course_cart_lec (lec_no),
  CONSTRAINT fk_course_cart_student FOREIGN KEY (std_no) REFERENCES student (std_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_course_cart_lecture FOREIGN KEY (lec_no) REFERENCES lecture (lec_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='장바구니';

CREATE TABLE enrollment (
  std_no VARCHAR(10) NOT NULL COMMENT '학번',
  lec_no INT NOT NULL COMMENT '강의번호',
  enrl_stat VARCHAR(10) NOT NULL DEFAULT 'ENROLLED' COMMENT '신청상태: ENROLLED(신청), CANCELLED(취소)',
  retake_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '재수강여부: Y(예), N(아니오)',
  proc_type VARCHAR(10) NOT NULL DEFAULT 'STUDENT' COMMENT '처리구분: STUDENT(학생 본인), ADMIN(관리자)',
  enrl_dtm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일시',
  cncl_dtm DATETIME NULL COMMENT '취소일시',
  CONSTRAINT pk_enrollment PRIMARY KEY (std_no, lec_no),
  CONSTRAINT ck_enrollment_enrl_stat CHECK (enrl_stat IN ('ENROLLED', 'CANCELLED')),
  CONSTRAINT ck_enrollment_retake_yn CHECK (retake_yn IN ('Y', 'N')),
  CONSTRAINT ck_enrollment_proc_type CHECK (proc_type IN ('STUDENT', 'ADMIN')),
  CONSTRAINT ck_enrollment_cancel_time CHECK ((enrl_stat = 'CANCELLED') = (cncl_dtm IS NOT NULL)),
  INDEX ix_enrollment_lec_stat (lec_no, enrl_stat),
  CONSTRAINT fk_enrollment_student FOREIGN KEY (std_no) REFERENCES student (std_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_enrollment_lecture FOREIGN KEY (lec_no) REFERENCES lecture (lec_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='수강신청';

CREATE TABLE grade (
  std_no VARCHAR(10) NOT NULL COMMENT '학번',
  lec_no INT NOT NULL COMMENT '강의번호',
  mid_scr TINYINT NULL COMMENT '중간점수',
  fin_scr TINYINT NULL COMMENT '기말점수',
  hw_scr TINYINT NULL COMMENT '과제점수',
  att_scr TINYINT NULL COMMENT '출석점수',
  tot_scr TINYINT NULL COMMENT '총점',
  ltr_grd VARCHAR(2) NULL COMMENT '등급',
  CONSTRAINT pk_grade PRIMARY KEY (std_no, lec_no),
  CONSTRAINT ck_grade_mid_scr CHECK (mid_scr BETWEEN 0 AND 100),
  CONSTRAINT ck_grade_fin_scr CHECK (fin_scr BETWEEN 0 AND 100),
  CONSTRAINT ck_grade_hw_scr CHECK (hw_scr BETWEEN 0 AND 100),
  CONSTRAINT ck_grade_att_scr CHECK (att_scr BETWEEN 0 AND 100),
  CONSTRAINT ck_grade_tot_scr CHECK (tot_scr BETWEEN 0 AND 100),
  CONSTRAINT ck_grade_ltr_grd CHECK (ltr_grd IN ('A+', 'A0', 'A-', 'B+', 'B0', 'B-', 'C+', 'C0', 'C-', 'D+', 'D0', 'D-', 'F', 'P')),
  CONSTRAINT ck_grade_pf_no_score CHECK (ltr_grd <> 'P' OR (mid_scr IS NULL AND fin_scr IS NULL AND hw_scr IS NULL AND att_scr IS NULL AND tot_scr IS NULL)),
  INDEX ix_grade_lec (lec_no),
  CONSTRAINT fk_grade_enrollment FOREIGN KEY (std_no, lec_no) REFERENCES enrollment (std_no, lec_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='성적';

CREATE TABLE teaching_assistant_assignment (
  lec_no INT NOT NULL COMMENT '강의번호',
  std_no VARCHAR(10) NOT NULL COMMENT '학번',
  CONSTRAINT pk_teaching_assistant_assignment PRIMARY KEY (lec_no, std_no),
  INDEX ix_teaching_assistant_assignment_std (std_no),
  CONSTRAINT fk_teaching_assistant_assignment_lecture FOREIGN KEY (lec_no) REFERENCES lecture (lec_no) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_teaching_assistant_assignment_student FOREIGN KEY (std_no) REFERENCES student (std_no) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='조교배정';
