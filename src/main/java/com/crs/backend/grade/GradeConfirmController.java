package com.crs.backend.grade;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.grade.dto.GradeConfirmRequest;
import com.crs.backend.grade.dto.GradeConfirmResponse;
import com.crs.backend.grade.dto.GradeStatusResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 성적 확정(GRD-02). /api/admin/** 라 관리자만 부릅니다. */
@RestController
@RequestMapping("/api/admin/grades")
public class GradeConfirmController {

    private final GradeConfirmService gradeConfirmService;

    public GradeConfirmController(GradeConfirmService gradeConfirmService) {
        this.gradeConfirmService = gradeConfirmService;
    }

    // 학기별 성적상태. semNo 를 빼면 현재 학기, grdStat=NOT_ENTERED 면 미입력 강의 목록
    @GetMapping
    public GradeStatusResponse status(
            @RequestParam(required = false) Integer semNo, @RequestParam(required = false) GrdStat grdStat) {
        return gradeConfirmService.status(semNo, grdStat);
    }

    // 확정. 강의 하나만 확정할 때도 목록으로 보낸다
    @PostMapping("/confirm")
    public GradeConfirmResponse confirm(CurrentUser user, @Valid @RequestBody GradeConfirmRequest request) {
        return gradeConfirmService.confirm(user, request);
    }
}
