package com.crs.backend.lecture;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.lecture.dto.TeachingAssistantListResponse;
import com.crs.backend.lecture.dto.TeachingAssistantRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 조교 관리(LEC-04). 담당 교수가 강의의 조교를 보고 추가·삭제합니다. 세 API 모두 처리 후의 조교 목록을 돌려줍니다. */
@RestController
@RequestMapping("/api/teaching/lectures/{lecNo}/assistants")
public class TeachingAssistantController {

    private final TeachingAssistantService teachingAssistantService;

    public TeachingAssistantController(TeachingAssistantService teachingAssistantService) {
        this.teachingAssistantService = teachingAssistantService;
    }

    // 조교 목록
    @GetMapping
    public TeachingAssistantListResponse list(CurrentUser user, @PathVariable int lecNo) {
        return teachingAssistantService.list(user, lecNo);
    }

    // 조교 추가
    @PostMapping
    public TeachingAssistantListResponse add(
            CurrentUser user, @PathVariable int lecNo, @Valid @RequestBody TeachingAssistantRequest request) {
        return teachingAssistantService.add(user, lecNo, request.stdNo());
    }

    // 조교 삭제
    @DeleteMapping("/{stdNo}")
    public TeachingAssistantListResponse remove(CurrentUser user, @PathVariable int lecNo, @PathVariable String stdNo) {
        return teachingAssistantService.remove(user, lecNo, stdNo);
    }
}
