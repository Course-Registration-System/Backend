package com.crs.backend.common;

import org.springframework.http.HttpStatus;

/** 오류 종류. 응답의 code 에 enum 이름이 들어가고, 프론트는 이 값으로 경우를 구분합니다. */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학과를 찾을 수 없습니다."),
    DUPLICATE_USER_NO(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다."),

    LECTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "과목을 찾을 수 없습니다."),
    SEMESTER_NOT_FOUND(HttpStatus.NOT_FOUND, "학기를 찾을 수 없습니다."),
    // 학기·학사일정 (ACA-01)
    DUPLICATE_SEMESTER(HttpStatus.CONFLICT, "이미 등록된 학기입니다."),
    SEMESTER_IN_USE(HttpStatus.CONFLICT, "일정이 시작된 학기는 연도·학기구분을 바꿀 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "학사일정을 찾을 수 없습니다."),
    SCHEDULE_OVERLAP(HttpStatus.CONFLICT, "같은 일정유형·대상학년의 기간과 겹칩니다."),
    SCHEDULE_ORDER(HttpStatus.CONFLICT, "일정은 장바구니 → 수강신청 → 정정 → 성적입력 순서여야 합니다."),
    SCHEDULE_STARTED(HttpStatus.CONFLICT, "이미 시작된 일정은 삭제할 수 없습니다."),
    GRADES_NOT_CONFIRMED(HttpStatus.CONFLICT, "직전 정규학기 이후 성적이 확정되지 않은 강의가 있습니다."),
    MAX_CREDIT_CLOSED(HttpStatus.CONFLICT, "학사일정이 시작된 학기나 지난 학기는 최대학점을 산정할 수 없습니다."),
    PROFESSOR_NOT_FOUND(HttpStatus.NOT_FOUND, "담당할 수 있는 교수가 아닙니다."),
    CLASSROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "강의실을 찾을 수 없습니다."),
    DUPLICATE_SECTION(HttpStatus.CONFLICT, "같은 학기·과목에 이미 있는 분반입니다."),
    LECTURE_OPEN_CLOSED(HttpStatus.CONFLICT, "장바구니 기간이 시작돼 강의를 새로 개설할 수 없습니다."),
    ROOM_TIME_CONFLICT(HttpStatus.CONFLICT, "강의실 사용 시간이 다른 강의와 겹칩니다."),
    PROFESSOR_TIME_CONFLICT(HttpStatus.CONFLICT, "담당 교수의 다른 강의와 시간이 겹칩니다."),
    UPDATE_PERIOD_OVER(HttpStatus.CONFLICT, "정정 기간이 끝나 강의를 수정할 수 없습니다."),
    TIME_CHANGE_CLOSED(HttpStatus.CONFLICT, "수강신청 기간이 시작돼 강의시간·수업유형을 바꿀 수 없습니다."),
    CAPACITY_BELOW_ENROLLED(HttpStatus.CONFLICT, "정원을 현재 신청인원보다 작게 줄일 수 없습니다."),
    ROOM_REQUIRED(HttpStatus.CONFLICT, "정정 기간이 시작돼 대면 수업은 강의실을 지정해야 합니다."),
    LECTURE_CLOSED(HttpStatus.CONFLICT, "폐강된 강의입니다."),
    CLOSE_PERIOD_OVER(HttpStatus.CONFLICT, "정정 기간이 끝나 폐강할 수 없습니다."),

    NO_REGISTRATION_SEMESTER(HttpStatus.CONFLICT, "지금은 신청 대상 학기가 없습니다."),
    CART_CLOSED(HttpStatus.CONFLICT, "장바구니 기간이 아닙니다."),
    STUDENT_STATUS_NOT_ALLOWED(HttpStatus.FORBIDDEN, "이번 학기에 신청할 수 없는 학적상태입니다."),
    OTHER_SEMESTER_LECTURE(HttpStatus.CONFLICT, "신청 대상 학기의 강의가 아닙니다."),
    DUPLICATE_CART(HttpStatus.CONFLICT, "이미 장바구니에 담은 강의입니다."),
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니에 없는 강의입니다."),

    ENROLL_CLOSED(HttpStatus.CONFLICT, "수강신청 기간이 아닙니다."),
    ALREADY_ENROLLED_COURSE(HttpStatus.CONFLICT, "같은 과목을 이미 신청했습니다."),
    RETAKE_NOT_ALLOWED(HttpStatus.CONFLICT, "재수강할 수 없는 과목입니다. 이전 성적이 C+ 이하(P/F 과목은 F)여야 합니다."),
    RETAKE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "재수강은 10과목까지 할 수 있습니다."),
    LECTURE_FULL(HttpStatus.CONFLICT, "정원이 찼습니다."),
    TIME_CONFLICT(HttpStatus.CONFLICT, "이미 신청한 강의와 시간이 겹칩니다."),
    CREDIT_EXCEEDED(HttpStatus.CONFLICT, "최대학점을 넘습니다."),
    PREREQUISITE_NOT_MET(HttpStatus.CONFLICT, "선수과목을 이수하지 않았습니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "신청한 강의가 아닙니다."),

    // 과목 (ACA-02). 과목·학과가 없을 때는 위의 COURSE_NOT_FOUND·DEPARTMENT_NOT_FOUND 를 같이 씁니다
    DUPLICATE_COURSE_CODE(HttpStatus.CONFLICT, "이미 등록된 과목코드입니다."),
    INVALID_CHAPEL_COURSE(HttpStatus.CONFLICT, "채플은 0.5학점, P/F 과목이어야 합니다."),
    PREREQUISITE_SELF(HttpStatus.CONFLICT, "자기 자신을 선수과목으로 지정할 수 없습니다."),
    PREREQUISITE_CYCLE(HttpStatus.CONFLICT, "선수과목이 순환합니다."),
    COURSE_IN_USE(HttpStatus.CONFLICT, "개설된 강의가 있는 과목은 과목명과 권장학년만 바꿀 수 있습니다."),

    // 조교 (LEC-04). 강의가 없거나 폐강일 때는 위의 LECTURE_NOT_FOUND·LECTURE_CLOSED, 학생이 없을 때는 USER_NOT_FOUND 를 같이 씁니다
    ASSISTANT_NOT_ELIGIBLE(HttpStatus.CONFLICT, "비활성 계정이거나 졸업한 학생은 조교로 지정할 수 없습니다."),
    ASSISTANT_IS_ENROLLED(HttpStatus.CONFLICT, "이 강의를 수강하는 학생은 조교가 될 수 없습니다."),
    DUPLICATE_ASSISTANT(HttpStatus.CONFLICT, "이미 이 강의의 조교입니다."),
    ASSISTANT_NOT_FOUND(HttpStatus.NOT_FOUND, "이 강의의 조교가 아닙니다."),
    ASSISTANT_LECTURE_FINISHED(HttpStatus.CONFLICT, "성적이 확정된 강의는 조교를 추가·삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
