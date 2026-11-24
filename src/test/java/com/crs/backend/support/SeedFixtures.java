package com.crs.backend.support;

/**
 * 시드 데이터의 사례를 이름으로 가리키는 상수. tools/seed/build_seed.py 가 만든다. 직접 고치지 않는다.
 *
 * <p>설명은 docs/seed-data.md 에 있다.
 */
public final class SeedFixtures {

    private SeedFixtures() {}

    /** 모든 계정의 비밀번호. */
    public static final String PASSWORD = "test1234!";

    /** 학기번호. */
    public static final class Semester {

        private Semester() {}

        /** 2026-2. T0의 성적 입력 학기, T1까지 현재 학기 */
        public static final int Y2026_SECOND = 9;

        /** 2026 겨울학기 */
        public static final int Y2026_WINTER = 10;

        /** 2027-1. 수강신청 학기, s3 이후 현재 학기 */
        public static final int Y2027_FIRST = 11;
    }

    /** 학생이 아닌 계정. */
    public static final class Account {

        private Account() {}

        /** 관리자 */
        public static final String ADMIN = "90001";

        /** 성적 입력 시연 강의의 교수 */
        public static final String DEMO_PROFESSOR = "100002";

        /** 퇴직 교수(INACTIVE). 로그인 차단 */
        public static final String PROFESSOR_INACTIVE = "100006";
    }

    /** 학번. */
    public static final class Student {

        private Student() {}

        /** 시연 학생. 2026-1 전부 A-, 2026-2 시연 강의 점수만 빈칸, 나머지 A-. A-를 넣으면 1학년 평점 4.0 → 2027-1 최대학점 21 */
        public static final String DEMO = "20260001";

        /** 2026-2 평점 4.0 → 최대학점 21 */
        public static final String GPA_EXACTLY_4 = "20240001";

        /** 2026-2 평점 4.0 미만 → 최대학점 18. 신청 취소 이력 */
        public static final String GPA_JUST_UNDER_4 = "20240002";

        /** 2026-2 P/F 과목만 → 평점 없음 → 최대학점 18 */
        public static final String PASS_FAIL_ONLY = "20250003";

        /** s1 에서 졸업 처리(정규 8학기, 채플 뺀 130학점 이상, 채플 P 6학기 이상) */
        public static final String GRADUATING = "20230001";

        /** 휴학생. 2027-1 최대학점 없음 */
        public static final String ON_LEAVE = "20230003";

        /** T0 휴학 → s1 에서 복학. 2학년 1학기라 1학년(2025) 평점 4.3 → 최대학점 21 */
        public static final String RETURNING = "20250002";

        /** 2027 신입생. s1 에서 계정 생성 */
        public static final String FRESHMAN = "20270001";

        /** 재수강 이력(F → A- 상한). 2027-1 데이터베이스 재수강 신청 */
        public static final String RETAKE = "20240003";

        /** 자료구조 D+ → 데이터베이스(최소 C0) 신청 불가(T2) */
        public static final String PREREQ_NOT_MET = "20240004";

        /** 조교. 2027-1 198번 강의 */
        public static final String TEACHING_ASSISTANT = "20240005";

        /** 대학원생 조교(학번 10자리). 2027-1 198번 강의. 장바구니·신청·최대학점 대상 아님 */
        public static final String GRADUATE_ASSISTANT = "2026710001";

        /** 2026-2 관리자 수동 추가 1건. 3학년이라 T1 에 신청 불가 */
        public static final String ADMIN_ADDED = "20250001";

        /** 정원 마감 강의가 장바구니에 있음(T1) */
        public static final String SOPHOMORE_FULL_IN_CART = "20260001";

        /** 장바구니 강의를 모두 신청하면 최대학점 초과(T1) */
        public static final String OVER_MAX_CREDIT = "20260002";

        /** 장바구니 강의 두 개의 시간이 겹침(T1) */
        public static final String TIME_CONFLICT = "20250002";

        /** 장바구니 강의의 선수과목 미이수(T1) */
        public static final String PREREQ_NOT_MET_SOPHOMORE = "20260003";
    }

    /** 강의번호. */
    public static final class Lecture {

        private Lecture() {}

        /** 2026-2 성적 입력 시연(입력중, 수강생 12명, A 한도 4명) */
        public static final int DEMO_GRADE = 182;

        /** 2026-2 성적 미입력 */
        public static final int NOT_ENTERED = 158;

        /** 2027-1 정원 마감(s2 이후) */
        public static final int FULL = 190;

        /** 2027-1 한 자리 남음(s2 이후) */
        public static final int ONE_SEAT_LEFT = 197;

        /** DEMO 학생이 21학점을 채우려고 더 담은 강의 */
        public static final int DEMO_EXTRA = 203;

        /** OVER_MAX_CREDIT 학생이 더 담은 강의 */
        public static final int OVER_MAX_CREDIT_TARGET = 196;

        /** TIME_CONFLICT 학생이 더 담은, 시간이 겹치는 강의 */
        public static final int TIME_CONFLICT_TARGET = 208;

        /** PREREQ_NOT_MET_SOPHOMORE 학생이 담은, 선수과목이 있는 강의 */
        public static final int PREREQ_TARGET = 202;

        /** 2027-1 온라인(강의실 없음) */
        public static final int ONLINE = 221;

        /** 2027-1 대면인데 강의실 미배정 */
        public static final int NO_ROOM = 224;

        /** 2027-1 폐강 */
        public static final int CLOSED = 226;

        /** 같은 과목의 2분반 */
        public static final int TWO_SECTIONS = 217;

        /** 전공 영어강의 → 상대평가 제외 */
        public static final int ENGLISH_MAJOR = 194;

        /** 교양 영어강의 → 상대평가 대상 */
        public static final int ENGLISH_GENERAL = 223;

        /** 상대평가 제외 인정 */
        public static final int RELATIVE_EXEMPT = 206;

        /** 반영비율 미입력 */
        public static final int NO_WEIGHTS = 195;

        /** RETAKE 학생이 재수강 신청한 강의 */
        public static final int RETAKE_TARGET = 193;

        /** TEACHING_ASSISTANT 학생이 조교인 강의 */
        public static final int TA_TARGET = 198;

        /** TEACHING_ASSISTANT 학생이 2026-2 에 조교인 강의 */
        public static final int TA_CURRENT = 162;

        /** 2027-1, 시연 교수(DEMO_PROFESSOR) 담당, 점수제, 계획서 경로·반영비율 있음(파일은 없음). T0 에 계획서를 고칠 수 있다 */
        public static final int PLAN_EDITABLE = 187;
    }
}
