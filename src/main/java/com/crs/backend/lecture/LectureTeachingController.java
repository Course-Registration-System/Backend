package com.crs.backend.lecture;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.lecture.dto.TeachingLectureListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 교수·조교의 강의 운영 기능. 지금은 담당 강의 조회(LEC-01)만 있고, 수강생 명단·강의계획서·조교·성적은 /api/teaching/lectures/{lecNo}/…
 * 아래에 이어 붙입니다.
 */
@RestController
@RequestMapping("/api/teaching/lectures")
public class LectureTeachingController {

    private final LectureTeachingService lectureTeachingService;

    public LectureTeachingController(LectureTeachingService lectureTeachingService) {
        this.lectureTeachingService = lectureTeachingService;
    }

    // 담당 강의 목록. semNo 를 빼면 현재 학기
    @GetMapping
    public TeachingLectureListResponse list(CurrentUser user, @RequestParam(required = false) Integer semNo) {
        return lectureTeachingService.list(user, semNo);
    }
}
