package com.crs.backend.academic;

import com.crs.backend.academic.dto.DashboardResponse;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 조회(ACA-04). 관리자 전용이며 데이터를 바꾸지 않습니다. */
@Service
public class DashboardService {

    private final DashboardRepository repository;
    private final SchedulePolicy schedulePolicy;

    public DashboardService(DashboardRepository repository, SchedulePolicy schedulePolicy) {
        this.repository = repository;
        this.schedulePolicy = schedulePolicy;
    }

    // 네 집계가 같은 시점을 보도록 한 트랜잭션에서 읽는다
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(CurrentUser user, Integer semNo) {
        if (!user.is(Role.ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        int sem = semNo == null ? targetSemester() : checkedSemester(semNo);
        return new DashboardResponse(
                sem,
                repository.countOpenLectures(sem),
                repository.findFullLectures(sem),
                repository.countUnassignedLectures(sem),
                repository.findMissingPlanLectures(sem));
    }

    // 학기를 비우면 신청 대상 학기(D24), 그것도 없으면(정정까지 끝남) 현재 학기
    private int targetSemester() {
        return schedulePolicy
                .registrationSemester()
                .or(repository::findCurrentSemester)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEMESTER_NOT_FOUND));
    }

    private int checkedSemester(int semNo) {
        if (!repository.semesterExists(semNo)) {
            throw new BusinessException(ErrorCode.SEMESTER_NOT_FOUND);
        }
        return semNo;
    }
}
