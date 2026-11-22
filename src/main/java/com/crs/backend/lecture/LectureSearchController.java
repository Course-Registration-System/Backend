package com.crs.backend.lecture;

import com.crs.backend.lecture.dto.LectureSearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 강의 검색(REG-01). 로그인한 모든 사용자가 씁니다. */
@RestController
@RequestMapping("/api/lectures")
public class LectureSearchController {

    private final LectureSearchService lectureSearchService;

    public LectureSearchController(LectureSearchService lectureSearchService) {
        this.lectureSearchService = lectureSearchService;
    }

    /**
     * 예: /api/lectures?deptNo=101&yrLvl=2&cmplType=MAJOR_REQ&crsNm=자료&profNm=김&page=0&size=20
     *
     * <p>semNo 를 비우면 신청 대상 학기입니다. 학년 조건에서 권장학년이 없는 과목(교양·채플 등)은 모든 학년에 나옵니다.
     */
    @GetMapping
    public LectureSearchResponse search(
            @RequestParam(required = false) Integer semNo,
            @RequestParam(required = false) Integer deptNo,
            @RequestParam(required = false) Integer yrLvl,
            @RequestParam(required = false) String cmplType,
            @RequestParam(required = false) String crsNm,
            @RequestParam(required = false) String profNm,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return lectureSearchService.search(semNo, deptNo, yrLvl, cmplType, crsNm, profNm, page, size);
    }
}
