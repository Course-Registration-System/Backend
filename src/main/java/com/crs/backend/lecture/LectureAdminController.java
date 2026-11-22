package com.crs.backend.lecture;

import com.crs.backend.lecture.dto.LectureCreateRequest;
import com.crs.backend.lecture.dto.LectureResponse;
import com.crs.backend.lecture.dto.LectureUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 강의 개설 관리(ACA-03). 관리자 전용. 학생용 강의 검색(REG-01)은 따로 있습니다. */
@RestController
@RequestMapping("/api/admin/lectures")
public class LectureAdminController {

    private final LectureAdminService lectureAdminService;

    public LectureAdminController(LectureAdminService lectureAdminService) {
        this.lectureAdminService = lectureAdminService;
    }

    /** 예: /api/admin/lectures?semNo=11 */
    @GetMapping
    public List<LectureResponse> list(@RequestParam int semNo) {
        return lectureAdminService.list(semNo);
    }

    @GetMapping("/{lecNo}")
    public LectureResponse get(@PathVariable int lecNo) {
        return lectureAdminService.get(lecNo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LectureResponse create(@Valid @RequestBody LectureCreateRequest request) {
        return lectureAdminService.create(request);
    }

    @PutMapping("/{lecNo}")
    public LectureResponse update(@PathVariable int lecNo, @Valid @RequestBody LectureUpdateRequest request) {
        return lectureAdminService.update(lecNo, request);
    }

    @PatchMapping("/{lecNo}/close")
    public LectureResponse close(@PathVariable int lecNo) {
        return lectureAdminService.close(lecNo);
    }
}
