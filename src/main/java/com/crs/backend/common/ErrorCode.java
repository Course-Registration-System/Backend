package com.crs.backend.common;

import org.springframework.http.HttpStatus;

/** 오류 종류. 응답의 code 에 enum 이름이 들어가고, 프론트는 이 값으로 경우를 구분합니다. */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학과를 찾을 수 없습니다."),
    DUPLICATE_USER_NO(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다."),

    LECTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "과목을 찾을 수 없습니다."),
    SEMESTER_NOT_FOUND(HttpStatus.NOT_FOUND, "학기를 찾을 수 없습니다."),
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
    CLOSE_PERIOD_OVER(HttpStatus.CONFLICT, "정정 기간이 끝나 폐강할 수 없습니다.");

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
