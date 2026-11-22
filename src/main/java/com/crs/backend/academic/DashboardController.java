package com.crs.backend.academic;

import com.crs.backend.academic.dto.DashboardResponse;
import com.crs.backend.common.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드 조회(ACA-04). 관리자 전용입니다. */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** semNo 를 비우면 신청 대상 학기, 신청 대상 학기가 없으면 현재 학기입니다. */
    @GetMapping("/api/admin/dashboard")
    public DashboardResponse dashboard(CurrentUser user, @RequestParam(required = false) Integer semNo) {
        return dashboardService.dashboard(user, semNo);
    }
}
