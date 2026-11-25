package com.crs.backend.lecture;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.lecture.dto.TeachingStudentListResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수강생 명단 조회(LEC-02). 담당 교수와 권한 있는 조교가 신청 종료 뒤에 봅니다. */
@Service
public class TeachingStudentService {

    private final TeachingStudentRepository teachingStudentRepository;
    private final LectureAccess lectureAccess;
    private final SchedulePolicy schedulePolicy;
    private final Clock clock;

    public TeachingStudentService(
            TeachingStudentRepository teachingStudentRepository,
            LectureAccess lectureAccess,
            SchedulePolicy schedulePolicy,
            Clock clock) {
        this.teachingStudentRepository = teachingStudentRepository;
        this.lectureAccess = lectureAccess;
        this.schedulePolicy = schedulePolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeachingStudentListResponse list(CurrentUser user, int lecNo) {
        // ① 없는 강의는 권한보다 먼저 404 로 알린다(LectureAccess 는 없는 강의도 403)
        int semNo = teachingStudentRepository
                .findSemNo(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));

        // ② 담당 교수이거나 권한 있는 조교
        lectureAccess.checkManage(user, lecNo);

        // ③ 신청 종료(정정 종료, 정정이 없으면 마지막 수강신청일 종료) 뒤부터. 신청 일정이 하나도 없는 학기는 바로 볼 수 있다
        LocalDateTime now = LocalDateTime.now(clock);
        boolean beforeEnd =
                schedulePolicy.registrationEnd(semNo).map(now::isBefore).orElse(false);
        if (beforeEnd) {
            throw new BusinessException(ErrorCode.ROSTER_NOT_OPEN);
        }

        // ④ 신청상태가 '신청'인 학생, 학번 순
        return new TeachingStudentListResponse(lecNo, teachingStudentRepository.findEnrolled(lecNo));
    }
}
