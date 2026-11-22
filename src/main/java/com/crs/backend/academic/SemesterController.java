package com.crs.backend.academic;

import com.crs.backend.academic.dto.ScheduleRequest;
import com.crs.backend.academic.dto.ScheduleResponse;
import com.crs.backend.academic.dto.SemesterRequest;
import com.crs.backend.academic.dto.SemesterResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 학기·학사일정(ACA-01). 조회는 로그인한 누구나, /api/admin/** 은 관리자만 들어옵니다(AuthInterceptor). */
@RestController
public class SemesterController {

    private final SemesterService semesterService;

    public SemesterController(SemesterService semesterService) {
        this.semesterService = semesterService;
    }

    @GetMapping("/api/semesters")
    public List<SemesterResponse> list() {
        return semesterService.list();
    }

    @GetMapping("/api/semesters/{semNo}/schedules")
    public List<ScheduleResponse> schedules(@PathVariable int semNo) {
        return semesterService.schedules(semNo);
    }

    @PostMapping("/api/admin/semesters")
    @ResponseStatus(HttpStatus.CREATED)
    public SemesterResponse create(@Valid @RequestBody SemesterRequest request) {
        return semesterService.create(request);
    }

    @PutMapping("/api/admin/semesters/{semNo}")
    public SemesterResponse update(@PathVariable int semNo, @Valid @RequestBody SemesterRequest request) {
        return semesterService.update(semNo, request);
    }

    @PutMapping("/api/admin/semesters/{semNo}/current")
    public SemesterResponse changeCurrent(@PathVariable int semNo) {
        return semesterService.changeCurrent(semNo);
    }

    @PostMapping("/api/admin/semesters/{semNo}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse addSchedule(@PathVariable int semNo, @Valid @RequestBody ScheduleRequest request) {
        return semesterService.addSchedule(semNo, request);
    }

    @DeleteMapping("/api/admin/schedules/{schdNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable int schdNo) {
        semesterService.deleteSchedule(schdNo);
    }
}
