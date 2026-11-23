package com.crs.backend.lecture;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.lecture.dto.TeachingStudentListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 수강생 명단 조회(LEC-02). 담당 강의(LEC-01)와 같은 /api/teaching/lectures 아래입니다. */
@RestController
@RequestMapping("/api/teaching/lectures/{lecNo}/students")
public class TeachingStudentController {

    private final TeachingStudentService teachingStudentService;

    public TeachingStudentController(TeachingStudentService teachingStudentService) {
        this.teachingStudentService = teachingStudentService;
    }

    @GetMapping
    public TeachingStudentListResponse list(CurrentUser user, @PathVariable int lecNo) {
        return teachingStudentService.list(user, lecNo);
    }
}
