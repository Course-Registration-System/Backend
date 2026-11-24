package com.crs.backend.lecture;

import com.crs.backend.lecture.LecturePlanService.PlanDownload;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 강의계획서 내려받기(LEC-03). 로그인한 사용자 모두 받을 수 있습니다. 강의 검색(REG-01) 응답의 planFile 이 있으면 프론트가 이 경로를
 * 부릅니다.
 *
 * <p>인증은 다른 API 처럼 {@code Authorization: Bearer} 헤더로만 합니다. {@code <a href>} 나 {@code window.open} 으로 바로 열면
 * 헤더가 붙지 않아 401 이므로, 프론트는 {@code fetch} 로 헤더를 붙여 받은 뒤 Blob URL 로 열어야 합니다.
 */
@RestController
public class LecturePlanDownloadController {

    private final LecturePlanService lecturePlanService;

    public LecturePlanDownloadController(LecturePlanService lecturePlanService) {
        this.lecturePlanService = lecturePlanService;
    }

    @GetMapping("/api/lectures/{lecNo}/plan")
    public ResponseEntity<Resource> download(@PathVariable int lecNo) {
        PlanDownload plan = lecturePlanService.download(lecNo);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(plan.fileName())
                                .build()
                                .toString())
                .body(new FileSystemResource(plan.file()));
    }
}
