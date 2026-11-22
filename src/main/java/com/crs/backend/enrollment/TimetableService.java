package com.crs.backend.enrollment;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.enrollment.dto.EnrollmentResponse;
import com.crs.backend.enrollment.dto.TimetableResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시간표 조회(REG-04). 학생 본인의 신청 강의를 강의시간과 함께 돌려줍니다. 수강신청 내역과 같은 목록입니다. 취소한 강의는 빠지고, 폐강하면
 * 그 강의의 신청이 모두 취소되므로 폐강 강의도 빠집니다.
 */
@Service
public class TimetableService {

    private final EnrollmentRepository enrollmentRepository;
    private final SchedulePolicy schedulePolicy;

    public TimetableService(EnrollmentRepository enrollmentRepository, SchedulePolicy schedulePolicy) {
        this.enrollmentRepository = enrollmentRepository;
        this.schedulePolicy = schedulePolicy;
    }

    @Transactional(readOnly = true)
    public TimetableResponse timetable(CurrentUser user, Integer semNo) {
        if (!user.is(Role.STUDENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String stdNo = user.usrNo();
        int sem = schedulePolicy.semesterOrDefault(semNo);

        List<EnrollmentResponse> lectures = enrollmentRepository.findEnrolled(stdNo, sem);
        BigDecimal total = sum(lectures, false);
        BigDecimal credits = sum(lectures, true);
        return new TimetableResponse(sem, lectures, total, credits, schedulePolicy.maxCredit(stdNo, sem));
    }

    // 채플은 총 신청 학점에는 넣고, 최대학점과 비교하는 학점에서는 뺀다(D8)
    private static BigDecimal sum(List<EnrollmentResponse> lectures, boolean withoutChapel) {
        return lectures.stream()
                .filter(lecture -> !(withoutChapel && "CHAPEL".equals(lecture.cmplType())))
                .map(EnrollmentResponse::crdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
