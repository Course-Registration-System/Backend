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

        /** 2026-2. T0~T2의 현재 학기(성적 확정 완료) */
        public static final int Y2026_SECOND = 9;

        /** 2026 겨울학기 */
        public static final int Y2026_WINTER = 10;

        /** 2027-1. 신청 대상 학기, T3의 현재 학기 */
        public static final int Y2027_FIRST = 11;
    }

    /** 학생이 아닌 계정. */
    public static final class Account {

        private Account() {}

        /** 관리자 */
        public static final String ADMIN = "9001";

        /** 교수. 2027-1 담당 강의 8개 */
        public static final String PROFESSOR = "100001";

        /** 퇴직 교수(INACTIVE). 로그인 차단 */
        public static final String PROFESSOR_INACTIVE = "100006";
    }

    /** 학번. */
    public static final class Student {

        private Student() {}

        /** 졸업생. 2027-1 장바구니·신청·최대학점 없음 */
        public static final String GRADUATED = "20230001";

        /** 휴학생. 2027-1 최대학점 없음 */
        public static final String ON_LEAVE = "20230003";

        /** 복학생. 휴학 직전 학기 평점 4.3 → 최대학점 21 */
        public static final String RETURNED = "20250002";

        /** 2027 신입생. 이력 없음 */
        public static final String FRESHMAN = "20270001";

        /** 2026-2 평점 4.0 → 최대학점 21 */
        public static final String GPA_EXACTLY_4 = "20240001";

        /** 2026-2 평점 4.0 미만 → 최대학점 18. 신청 취소 이력 */
        public static final String GPA_JUST_UNDER_4 = "20240002";

        /** 2026-2 P/F 과목만 → 평점 없음 → 최대학점 18 */
        public static final String PASS_FAIL_ONLY = "20250003";

        /** 재수강 이력(F → A- 상한). 2027-1 재수강 신청, 컴퓨터네트워크(C+) 재수강 가능 */
        public static final String RETAKE = "20240003";

        /** 자료구조 D+ → 데이터베이스(최소 C0) 신청 불가. 장바구니에는 있음 */
        public static final String PREREQ_NOT_MET = "20240004";

        /** 조교. 2027-1 199번 강의 */
        public static final String TEACHING_ASSISTANT = "20240005";

        /** 2026-2 관리자 수동 추가 1건 */
        public static final String ADMIN_ADDED = "20250001";

        /** 2학년. 정원 마감 강의가 장바구니에 있음(T1) */
        public static final String SOPHOMORE_FULL_IN_CART = "20260001";

        /** 장바구니 강의가 신청한 강의와 시간이 겹침(T2) */
        public static final String TIME_CONFLICT = "20230002";

        /** 장바구니 강의를 더하면 최대학점(18) 초과(T2) */
        public static final String OVER_MAX_CREDIT = "20250001";
    }

    /** 강의번호. */
    public static final class Lecture {

        private Lecture() {}

        /** 2027-1 정원 마감 */
        public static final int FULL = 191;

        /** 2027-1 한 자리 남음 */
        public static final int ONE_SEAT_LEFT = 198;

        /** 2027-1 온라인(강의실 없음) */
        public static final int ONLINE = 222;

        /** 2027-1 대면인데 강의실 미배정 */
        public static final int NO_ROOM = 225;

        /** 2027-1 폐강 */
        public static final int CLOSED = 227;

        /** 같은 과목의 2분반 */
        public static final int TWO_SECTIONS = 218;

        /** 전공 영어강의 → 상대평가 제외 */
        public static final int ENGLISH_MAJOR = 195;

        /** 교양 영어강의 → 상대평가 대상 */
        public static final int ENGLISH_GENERAL = 224;

        /** 상대평가 제외 인정 */
        public static final int RELATIVE_EXEMPT = 207;

        /** 반영비율 미입력 */
        public static final int NO_WEIGHTS = 196;

        /** TIME_CONFLICT 학생이 담은, 시간이 겹치는 강의 */
        public static final int TIME_CONFLICT_TARGET = 188;

        /** OVER_MAX_CREDIT 학생이 담은 강의 */
        public static final int OVER_MAX_CREDIT_TARGET = 204;

        /** RETAKE 학생이 재수강 신청한 강의 */
        public static final int RETAKE_TARGET = 194;

        /** TEACHING_ASSISTANT 학생이 조교인 강의 */
        public static final int TA_TARGET = 199;

        /** 2026 겨울 확정 */
        public static final int WINTER_CONFIRMED = 185;

        /** 2026 겨울 제출(확정 대기) */
        public static final int WINTER_SUBMITTED = 186;

        /** 2026 겨울 입력중(점수 빈 학생 1명) */
        public static final int WINTER_ENTERING = 184;

        /** T3 오버레이 후 입력중(점수 빈 학생 1명) */
        public static final int T3_ENTERING = 192;

        /** T3 오버레이 후 제출 */
        public static final int T3_SUBMITTED = 201;

        /** T3 오버레이 후 수강생 13명, 상대평가 대상 */
        public static final int T3_RELATIVE = 229;
    }
}
