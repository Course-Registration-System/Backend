package com.crs.backend.grade;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.grade.dto.GradeSaveRequest;
import com.crs.backend.grade.dto.GradeSheetResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 성적 입력·수정(GRD-01). 담당 강의(LEC-01)와 같은 /api/teaching/lectures 아래입니다. 세 API 모두 처리 후의 성적표를 돌려줍니다. */
@RestController
@RequestMapping("/api/teaching/lectures/{lecNo}/grades")
public class GradeEntryController {

    private final GradeEntryService gradeEntryService;

    public GradeEntryController(GradeEntryService gradeEntryService) {
        this.gradeEntryService = gradeEntryService;
    }

    // 성적표. 기간과 관계없이 볼 수 있다
    @GetMapping
    public GradeSheetResponse sheet(CurrentUser user, @PathVariable int lecNo) {
        return gradeEntryService.sheet(user, lecNo);
    }

    // 점수 입력·수정(여러 명). 성적 입력 기간에만
    @PutMapping
    public GradeSheetResponse save(
            CurrentUser user, @PathVariable int lecNo, @Valid @RequestBody GradeSaveRequest request) {
        return gradeEntryService.save(user, lecNo, request);
    }

    // 제출. 성적 입력 기간에만
    @PostMapping("/submit")
    public GradeSheetResponse submit(CurrentUser user, @PathVariable int lecNo) {
        return gradeEntryService.submit(user, lecNo);
    }
}
