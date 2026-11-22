package com.crs.backend.grade;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.grade.dto.GradeReportResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 성적 조회(GRD-03). 학생 본인의 확정된 성적만 보여 줍니다. */
@RestController
public class GradeReportController {

    private final GradeReportService gradeReportService;

    public GradeReportController(GradeReportService gradeReportService) {
        this.gradeReportService = gradeReportService;
    }

    /** 전체 학기의 성적표와 학기 평점, 누적 평점, 누적 이수 학점. 학기 선택은 화면이 합니다. */
    @GetMapping("/api/me/grades")
    public GradeReportResponse grades(CurrentUser user) {
        return gradeReportService.report(user);
    }
}
