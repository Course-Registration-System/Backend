package com.crs.backend.enrollment;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.enrollment.dto.EnrollRequest;
import com.crs.backend.enrollment.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 수강신청·취소(REG-03). 학생 전용이며, 학기는 서버가 신청 대상 학기로 정합니다. */
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /** 이번 학기 내 신청 내역 */
    @GetMapping
    public List<EnrollmentResponse> list(CurrentUser user) {
        return enrollmentService.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse enroll(CurrentUser user, @Valid @RequestBody EnrollRequest request) {
        return enrollmentService.enroll(user, request.lecNo());
    }

    @DeleteMapping("/{lecNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(CurrentUser user, @PathVariable int lecNo) {
        enrollmentService.cancel(user, lecNo);
    }
}
