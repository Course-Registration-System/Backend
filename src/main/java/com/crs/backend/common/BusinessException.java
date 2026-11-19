package com.crs.backend.common;

/** 업무 규칙 위반. 던지면 GlobalExceptionHandler 가 ErrorCode 의 상태와 메시지로 응답합니다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    /** 기본 메시지 대신 구체적인 사유를 보여 줄 때. 예: 어떤 강의와 시간이 겹치는지 */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
