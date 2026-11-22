package com.crs.backend.enrollment;

import com.crs.backend.enrollment.dto.AdminEnrollRequest;
import com.crs.backend.enrollment.dto.EnrollmentStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 수강신청 현황 관리(REG-05). 관리자 전용. */
@RestController
@RequestMapping("/api/admin/lectures/{lecNo}/enrollments")
public class EnrollmentAdminController {

    private final EnrollmentService enrollmentService;

    public EnrollmentAdminController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public EnrollmentStatusResponse status(@PathVariable int lecNo) {
        return enrollmentService.status(lecNo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentStatusResponse add(@PathVariable int lecNo, @Valid @RequestBody AdminEnrollRequest request) {
        enrollmentService.addByAdmin(lecNo, request.stdNo());
        return enrollmentService.status(lecNo);
    }

    @DeleteMapping("/{stdNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable int lecNo, @PathVariable String stdNo) {
        enrollmentService.cancelByAdmin(lecNo, stdNo);
    }
}
