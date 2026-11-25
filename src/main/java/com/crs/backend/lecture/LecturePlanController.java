package com.crs.backend.lecture;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.lecture.dto.LecturePlanResponse;
import com.crs.backend.lecture.dto.PlanWeightsRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 강의계획서 관리(LEC-03). 담당 교수·권한 있는 조교가 씁니다. 등록·수정은 장바구니 기간 시작 전까지입니다. */
@RestController
@RequestMapping("/api/teaching/lectures/{lecNo}/plan")
public class LecturePlanController {

    private final LecturePlanService lecturePlanService;

    public LecturePlanController(LecturePlanService lecturePlanService) {
        this.lecturePlanService = lecturePlanService;
    }

    // 계획서 등록 여부, 반영 비율, 마감
    @GetMapping
    public LecturePlanResponse view(CurrentUser user, @PathVariable int lecNo) {
        return lecturePlanService.view(user, lecNo);
    }

    // 계획서 PDF 올리기·교체(multipart 파트 이름 file). 파트가 없으면 400 INVALID_PLAN_FILE
    @PutMapping(path = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LecturePlanResponse uploadFile(
            CurrentUser user,
            @PathVariable int lecNo,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        return lecturePlanService.uploadFile(user, lecNo, file);
    }

    // 평가 반영 비율(합 100). P/F 과목은 받지 않는다
    @PutMapping("/weights")
    public LecturePlanResponse updateWeights(
            CurrentUser user, @PathVariable int lecNo, @Valid @RequestBody PlanWeightsRequest request) {
        return lecturePlanService.updateWeights(user, lecNo, request);
    }
}
