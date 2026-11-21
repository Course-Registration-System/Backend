package com.crs.backend.common;

import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 예외를 ProblemDetail(RFC 9457) 응답으로 바꿉니다. 형식은 README 의 "오류 응답"을 따릅니다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        return problem(e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String detail =
                error == null ? ErrorCode.INVALID_INPUT.message() : error.getField() + ": " + error.getDefaultMessage();
        return problem(ErrorCode.INVALID_INPUT, detail);
    }

    /** JSON 형식이 깨졌거나, 쿼리 값이 빠졌거나 enum·숫자로 바뀌지 않을 때(예: ?role=TEACHER) */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ProblemDetail handleUnreadable(Exception e) {
        return problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.message());
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
