package com.crs.backend.enrollment;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.enrollment.dto.TimetableResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 시간표 조회(REG-04). 학생 전용입니다. */
@RestController
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    /** semNo 를 비우면 신청 대상 학기, 신청 대상 학기가 없으면 현재 학기입니다. */
    @GetMapping("/api/me/timetable")
    public TimetableResponse timetable(CurrentUser user, @RequestParam(required = false) Integer semNo) {
        return timetableService.timetable(user, semNo);
    }
}
