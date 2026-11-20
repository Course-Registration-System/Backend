package com.crs.backend.common;

import org.springframework.http.HttpStatus;

/** 오류 종류. 응답의 code 에 enum 이름이 들어가고, 프론트는 이 값으로 경우를 구분합니다. */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),

    // 과목 (ACA-02)
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "과목을 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학과를 찾을 수 없습니다."),
    DUPLICATE_COURSE_CODE(HttpStatus.CONFLICT, "이미 등록된 과목코드입니다."),
    INVALID_CHAPEL_COURSE(HttpStatus.CONFLICT, "채플은 0.5학점, P/F 과목이어야 합니다."),
    PREREQUISITE_SELF(HttpStatus.CONFLICT, "자기 자신을 선수과목으로 지정할 수 없습니다."),
    PREREQUISITE_CYCLE(HttpStatus.CONFLICT, "선수과목이 순환합니다.");

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
