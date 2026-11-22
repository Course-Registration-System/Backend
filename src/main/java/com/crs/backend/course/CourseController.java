package com.crs.backend.course;

import com.crs.backend.course.dto.CourseCreateRequest;
import com.crs.backend.course.dto.CourseResponse;
import com.crs.backend.course.dto.CourseUpdateRequest;
import com.crs.backend.course.dto.PrerequisitesRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 과목 관리(ACA-02). 관리자 전용이며 권한은 AuthInterceptor 가 /api/admin/** 에서 확인합니다. */
@RestController
@RequestMapping("/api/admin/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    // 과목 목록(학과·이수구분·과목명/과목코드 일부로 거름)
    @GetMapping
    public List<CourseResponse> list(
            @RequestParam(required = false) Integer deptNo,
            @RequestParam(required = false) String cmplType,
            @RequestParam(required = false) String keyword) {
        return courseService.list(deptNo, cmplType, keyword);
    }

    // 과목 상세
    @GetMapping("/{crsCd}")
    public CourseResponse get(@PathVariable String crsCd) {
        return courseService.get(crsCd);
    }

    // 과목 등록
    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseCreateRequest request) {
        CourseResponse created = courseService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/courses/" + created.crsCd()))
                .body(created);
    }

    // 과목 수정(과목코드 제외)
    @PutMapping("/{crsCd}")
    public CourseResponse update(@PathVariable String crsCd, @Valid @RequestBody CourseUpdateRequest request) {
        return courseService.update(crsCd, request);
    }

    // 선수과목 지정(목록 통째로 교체)
    @PutMapping("/{crsCd}/prerequisites")
    public CourseResponse replacePrerequisites(
            @PathVariable String crsCd, @Valid @RequestBody PrerequisitesRequest request) {
        return courseService.replacePrerequisites(crsCd, request.prerequisites());
    }
}
