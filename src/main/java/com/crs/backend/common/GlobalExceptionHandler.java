package com.crs.backend.common;

import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /** 업로드 파일이 spring.servlet.multipart.max-file-size 를 넘을 때(강의계획서 LEC-03). 없으면 500 으로 나간다 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return problem(ErrorCode.PLAN_FILE_TOO_LARGE, ErrorCode.PLAN_FILE_TOO_LARGE.message());
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
