package com.crs.backend.common;

import org.springframework.http.HttpStatus;

/** 오류 종류. 응답의 code 에 enum 이름이 들어가고, 프론트는 이 값으로 경우를 구분합니다. */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "비활성화된 계정입니다.");

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
